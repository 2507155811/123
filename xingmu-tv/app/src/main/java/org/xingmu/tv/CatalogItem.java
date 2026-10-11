package org.xingmu.tv;
public final class CatalogItem {
 public final java.util.List<String> urls;public final boolean operaOnly;
 public final String userAgent;public final String id,title,section,category,country,url,mode,source,license;
 public CatalogItem(String id,String title,String section,String category,String country,String url,String mode,String source,String license){this(id,title,section,category,country,url,mode,source,license,false);}
 public CatalogItem(String id,String title,String section,String category,String country,String url,String mode,String source,String license,boolean operaOnly){this(id,title,section,category,country,url,mode,source,license,operaOnly,java.util.Collections.singletonList(url));}
 public CatalogItem(String id,String title,String section,String category,String country,String url,String mode,String source,String license,boolean operaOnly,java.util.List<String> urls){this(id,title,section,category,country,url,mode,source,license,operaOnly,urls,"XingmuTV/1.3");}
 public CatalogItem(String id,String title,String section,String category,String country,String url,String mode,String source,String license,boolean operaOnly,java.util.List<String> urls,String userAgent){this.userAgent=userAgent;this.urls=java.util.Collections.unmodifiableList(new java.util.ArrayList<>(urls));this.operaOnly=operaOnly;this.id=id;this.title=title;this.section=section;this.category=category;this.country=country;this.url=url;this.mode=mode;this.source=source;this.license=license;}
 public boolean opera(){return category.matches("戏曲|京剧|豫剧|越剧|粤剧|黄梅戏|评剧|昆曲|秦腔|晋剧|沪剧|川剧|滇剧|湘剧|赣剧|花鼓戏|高甲戏|莆仙戏|河北梆子")&&(!live()||operaOnly);}
 public boolean live(){return !section.equals("films");}
}
