package com.fast.radio;

import android.content.*;
import android.net.TrafficStats;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.PlaybackException;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;
import org.json.*;
import java.io.*;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    private ListView customList, iranList, worldList, newsList;
    private TextView status, nowPlaying, qualityValue, usagePerMinute;
    private final List<RadioStation> custom=new ArrayList<>(), iran=new ArrayList<>(), world=new ArrayList<>(), favorites=new ArrayList<>();
    private StationAdapter customAdapter, iranAdapter, worldAdapter;
    private MediaController controller; private ListenableFuture<MediaController> controllerFuture;
    private Spinner regionSpinner, countrySpinner; private EditText search;
    private final List<RadioBrowserClient.CountryItem> countries=new ArrayList<>();
    private final String[] regions=RegionCatalog.REGIONS;
    private VerticalRulerView qualityRuler; private RadioStation selected;
    private LegacyMediaPlayerEngine legacyEngine;
    private final Handler usageHandler=new Handler(Looper.getMainLooper());
    private long usageBaseBytes=-1, usageMinuteStart=0; private int usageMinute=1;
    private final Runnable usageRunnable=new Runnable(){public void run(){updateRealUsage();usageHandler.postDelayed(this,1000);}};
    private final String[] newsNames={"Sputnik فارسی","BBC Persian","Iran International","VOA Persian","BBC News","NHK Japan"};

    @Override protected void onCreate(Bundle b){
        super.onCreate(b); setContentView(R.layout.activity_main); legacyEngine=new LegacyMediaPlayerEngine(this);
        bind(); loadAssets(); setupSpinners(); setupLists(); setupNews(); connectController(); loadCustomOnline();
        RadioBrowserClient.countries(new RadioBrowserClient.CountryCallback(){
            public void result(List<RadioBrowserClient.CountryItem>x){runOnUiThread(()->{countries.clear();countries.addAll(x);refreshCountries();loadIran();});}
            public void error(Exception e){runOnUiThread(()->{status.setText("RadioBrowser connection failed");loadIran();});}
        });
    }
    private void bind(){
        customList=findViewById(R.id.customList); iranList=findViewById(R.id.iranList); worldList=findViewById(R.id.worldList); newsList=findViewById(R.id.newsList);
        status=findViewById(R.id.status); nowPlaying=findViewById(R.id.nowPlaying); qualityValue=findViewById(R.id.qualityValue); usagePerMinute=findViewById(R.id.usagePerMinute);
        qualityRuler=findViewById(R.id.qualityRuler); regionSpinner=findViewById(R.id.regionSpinner); countrySpinner=findViewById(R.id.countrySpinner); search=findViewById(R.id.search);
        qualityRuler.setListener(v->{qualityValue.setText(v+" kbps");applyQualityLimit(v);status.setText("Quality limit: "+v+" kbps");});
        findViewById(R.id.play).setOnClickListener(v->playSelected());
        findViewById(R.id.stop).setOnClickListener(v->{if(controller!=null)controller.stop();legacyEngine.stop();stopUsageMeter();status.setText("Stopped");});
        findViewById(R.id.fav).setOnClickListener(v->{if(selected!=null)toggleFavorite(selected);});
        findViewById(R.id.searchButton).setOnClickListener(v->doSearch()); findViewById(R.id.saveList).setOnClickListener(v->saveWorldList()); findViewById(R.id.favorites).setOnClickListener(v->showFavorites());
        setupScroll(R.id.customUp,customList,true); setupScroll(R.id.customDown,customList,false); setupScroll(R.id.iranUp,iranList,true); setupScroll(R.id.iranDown,iranList,false);
        qualityRuler.setValue(15); qualityValue.setText("15 kbps");
    }
    private void setupScroll(int id,ListView l,boolean up){findViewById(id).setOnClickListener(v->{int p=l.getFirstVisiblePosition();l.setSelection(Math.max(0,p+(up?-8:8)));});}
    private void loadAssets(){try{JSONArray a=new JSONArray(readAsset("stations.json"));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);custom.add(new RadioStation(o.optString("name"),o.optString("url")));}}catch(Exception ignored){}loadFavorites();}
    private String readAsset(String n)throws Exception{BufferedReader r=new BufferedReader(new InputStreamReader(getAssets().open(n),"UTF-8"));StringBuilder b=new StringBuilder();String l;while((l=r.readLine())!=null)b.append(l);r.close();return b.toString();}
    private void setupLists(){StationAdapter.Listener l=new StationAdapter.Listener(){public void select(RadioStation s){showSelected(s);}public void favorite(RadioStation s){toggleFavorite(s);}};customAdapter=new StationAdapter(this,custom,l);iranAdapter=new StationAdapter(this,iran,l);worldAdapter=new StationAdapter(this,world,l);customList.setAdapter(customAdapter);iranList.setAdapter(iranAdapter);worldList.setAdapter(worldAdapter);}
    private void setupNews(){
        ArrayAdapter<String>a=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,newsNames){@Override public View getView(int p,View c,ViewGroup parent){TextView t=(TextView)super.getView(p,c,parent);t.setText(newsNames[p]+"   ★");t.setTextColor(android.graphics.Color.rgb(255,225,45));t.setTextSize(14);t.setPadding(10,6,4,6);return t;}};
        newsList.setAdapter(a);newsList.setOnItemClickListener((p,v,pos,id)->{String q=newsNames[pos].replace(" فارسی","");RadioBrowserClient.search(q,"",new RadioBrowserClient.StationCallback(){public void result(List<RadioStation>x){runOnUiThread(()->{world.clear();world.addAll(x);worldAdapter.notifyDataSetChanged();status.setText(newsNames[pos]+" • "+x.size()+" results");});}public void error(Exception e){runOnUiThread(()->status.setText("No live result for "+newsNames[pos]));}});});
    }
    private void showSelected(RadioStation s){selected=s;nowPlaying.setText(s.name+"  •  "+(s.country.isEmpty()?"":s.country)+"  •  "+(s.bitrate>0?s.bitrate+" kbps":"Auto"));status.setText("Selected • target "+qualityRuler.getValue()+" kbps");}
    private void connectController(){
        SessionToken token=new SessionToken(this,new ComponentName(this,RadioPlaybackService.class));controllerFuture=new MediaController.Builder(this,token).buildAsync();
        controllerFuture.addListener(()->{try{controller=controllerFuture.get();controller.addListener(new androidx.media3.common.Player.Listener(){@Override public void onPlayerError(PlaybackException error){fallbackToLegacy();}});}catch(Exception e){status.setText("Controller error");}},getMainExecutor());
    }
    private void playSelected(){
        if(selected==null){status.setText("Select a station first");return;} if(controller==null){status.setText("Player not ready");return;}
        legacyEngine.stop(); applyQualityLimit(qualityRuler.getValue()); controller.setMediaItem(MediaItem.fromUri(selected.url)); controller.prepare(); controller.play(); startUsageMeter(); nowPlaying.setText("▶ "+selected.name); status.setText("Trying stream • "+(selected.codec.isEmpty()?"auto":selected.codec));
    }
    private void fallbackToLegacy(){
        if(selected==null)return; String alt=selected.alternateUrl; if(alt==null||alt.isEmpty()||alt.equals(selected.url)){status.setText("Stream failed • trying compatibility player");try{legacyEngine.play(selected.url);startUsageMeter();}catch(Exception e){status.setText("Stream unavailable");}} else {status.setText("Stream failed • trying alternate URL");try{legacyEngine.play(alt);startUsageMeter();}catch(Exception e){status.setText("Stream unavailable");}}
    }
    private void applyQualityLimit(int kbps){if(controller==null)return;try{TrackSelectionParameters p=controller.getTrackSelectionParameters().buildUpon().setMaxAudioBitrate(Math.max(1000,kbps*1000)).build();controller.setTrackSelectionParameters(p);}catch(Exception ignored){}}
    private void loadCustomOnline(){RadioBrowserClient.topStations(new RadioBrowserClient.StationCallback(){public void result(List<RadioStation>x){runOnUiThread(()->{custom.clear();custom.addAll(x);customAdapter.notifyDataSetChanged();status.setText("Custom Radio • "+x.size()+" stations");});}public void error(Exception e){runOnUiThread(()->status.setText("Custom Radio: connection failed"));}});}
    private void loadIran(){RadioBrowserClient.byCountry("IR",new RadioBrowserClient.StationCallback(){public void result(List<RadioStation>x){runOnUiThread(()->{iran.clear();iran.addAll(x);iranAdapter.notifyDataSetChanged();});}public void error(Exception e){runOnUiThread(()->status.setText("Iran Radio list unavailable"));}});}
    private void setupSpinners(){regionSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,regions));regionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?>p){}public void onItemSelected(AdapterView<?>p,View v,int pos,long id){refreshCountries();}});countrySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?>p){}public void onItemSelected(AdapterView<?>p,View v,int pos,long id){if(pos>0)doCountrySearch();}});}
    private void refreshCountries(){if(regionSpinner.getSelectedItemPosition()<0)return;String r=regions[regionSpinner.getSelectedItemPosition()];List<String>names=new ArrayList<>();names.add("All countries");for(RadioBrowserClient.CountryItem c:countries)if(RegionCatalog.region(c.code).equals(r))names.add(c.toString());countrySpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));}
    private String selectedCountryCode(){if(countrySpinner.getSelectedItemPosition()<=0)return "";String wanted=String.valueOf(countrySpinner.getSelectedItem());for(RadioBrowserClient.CountryItem c:countries)if(c.toString().equals(wanted))return c.code;return "";}
    private void doCountrySearch(){String code=selectedCountryCode();if(code.isEmpty())return;RadioBrowserClient.byCountry(code,new RadioBrowserClient.StationCallback(){public void result(List<RadioStation>x){runOnUiThread(()->{world.clear();world.addAll(x);worldAdapter.notifyDataSetChanged();status.setText("Loaded "+x.size()+" stations");});}public void error(Exception e){runOnUiThread(()->status.setText("World search error"));}});}
    private void doSearch(){String q=search.getText().toString().trim();if(q.isEmpty()){doCountrySearch();return;}String code=selectedCountryCode();RadioBrowserClient.search(q,code,new RadioBrowserClient.StationCallback(){public void result(List<RadioStation>x){runOnUiThread(()->{world.clear();world.addAll(x);worldAdapter.notifyDataSetChanged();status.setText("Search: "+x.size()+" stations");});}public void error(Exception e){runOnUiThread(()->status.setText("Search error"));}});}
    private void toggleFavorite(RadioStation s){s.favorite=!s.favorite;if(s.favorite&&!favorites.contains(s))favorites.add(s);if(!s.favorite)favorites.remove(s);saveFavorites();customAdapter.notifyDataSetChanged();iranAdapter.notifyDataSetChanged();worldAdapter.notifyDataSetChanged();}
    private void loadFavorites(){try{File f=new File(getFilesDir(),"favorites.json");if(!f.exists())return;JSONArray a=new JSONArray(readFile(f));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);favorites.add(new RadioStation(o.optString("stationUuid"),o.optString("name"),o.optString("url"),o.optString("alternateUrl"),o.optString("country"),o.optString("countryCode"),o.optString("codec"),o.optInt("bitrate"),o.optString("homepage"),o.optString("favicon")));}}catch(Exception ignored){}}
    private void saveFavorites(){try{JSONArray a=new JSONArray();for(RadioStation s:favorites)a.put(toJson(s));writeFile(new File(getFilesDir(),"favorites.json"),a.toString());}catch(Exception ignored){}}
    private JSONObject toJson(RadioStation s)throws Exception{return new JSONObject().put("stationUuid",s.stationUuid).put("name",s.name).put("url",s.url).put("alternateUrl",s.alternateUrl).put("country",s.country).put("countryCode",s.countryCode).put("codec",s.codec).put("bitrate",s.bitrate).put("homepage",s.homepage).put("favicon",s.favicon);}
    private void saveWorldList(){try{JSONArray a=new JSONArray();for(RadioStation s:world)a.put(toJson(s));File f=new File(getFilesDir(),"saved_world_list_"+System.currentTimeMillis()+".json");writeFile(f,a.toString());status.setText("Saved: "+f.getName());}catch(Exception e){status.setText("Save failed");}}
    private void showFavorites(){world.clear();world.addAll(favorites);worldAdapter.notifyDataSetChanged();status.setText("Favorites: "+favorites.size());}
    private void startUsageMeter(){usageHandler.removeCallbacks(usageRunnable);usageBaseBytes=TrafficStats.getUidRxBytes(getApplicationInfo().uid);if(usageBaseBytes==TrafficStats.UNSUPPORTED)usageBaseBytes=0;usageMinuteStart=System.currentTimeMillis();usageMinute=1;usagePerMinute.setText("دقیقه 1: 0.000 MB");usageHandler.post(usageRunnable);}
    private void updateRealUsage(){long now=System.currentTimeMillis();if(usageMinuteStart==0)return;while(now-usageMinuteStart>=60000){usageMinuteStart+=60000;usageMinute++;usageBaseBytes=TrafficStats.getUidRxBytes(getApplicationInfo().uid);if(usageBaseBytes==TrafficStats.UNSUPPORTED)usageBaseBytes=0;}long current=TrafficStats.getUidRxBytes(getApplicationInfo().uid);if(current==TrafficStats.UNSUPPORTED)current=usageBaseBytes;double mb=Math.max(0,current-usageBaseBytes)/(1024.0*1024.0);usagePerMinute.setText(String.format(Locale.US,"دقیقه %d: %.3f MB",usageMinute,mb));}
    private void stopUsageMeter(){usageHandler.removeCallbacks(usageRunnable);usageMinuteStart=0;}
    private String readFile(File f)throws Exception{BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(f),"UTF-8"));StringBuilder b=new StringBuilder();String l;while((l=r.readLine())!=null)b.append(l);r.close();return b.toString();}
    private void writeFile(File f,String s)throws Exception{FileOutputStream o=new FileOutputStream(f);o.write(s.getBytes("UTF-8"));o.close();}
    @Override protected void onDestroy(){stopUsageMeter();legacyEngine.release();if(controllerFuture!=null)MediaController.releaseFuture(controllerFuture);super.onDestroy();}
}
