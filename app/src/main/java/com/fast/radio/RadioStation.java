package com.fast.radio;

public class RadioStation {
    public String stationUuid;
    public String name;
    public String url;
    public String alternateUrl;
    public String country;
    public String countryCode;
    public String codec;
    public int bitrate;
    public String homepage;
    public String favicon;
    public boolean favorite;

    public RadioStation(String name, String url) { this("", name, url, "", "", "", "", 0, "", ""); }
    public RadioStation(String uuid, String name, String url, String alternateUrl, String country, String countryCode, String codec, int bitrate, String homepage, String favicon) {
        this.stationUuid=uuid; this.name=name; this.url=url; this.alternateUrl=alternateUrl; this.country=country; this.countryCode=countryCode; this.codec=codec; this.bitrate=bitrate; this.homepage=homepage; this.favicon=favicon;
    }
    public RadioStation(String name, String url, String country, String countryCode, String codec, int bitrate, String homepage, String favicon) {
        this("", name, url, "", country, countryCode, codec, bitrate, homepage, favicon);
    }
    @Override public String toString() {
        StringBuilder b=new StringBuilder(name);
        if(!country.isEmpty()) b.append("  •  ").append(country);
        if(bitrate>0) b.append("  •  ").append(bitrate).append(" kbps");
        return b.toString();
    }
}
