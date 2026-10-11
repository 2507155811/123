package org.xingmu.tv;
import java.util.*;
/** Pure filtering shared by touch and remote-control browsing. */
public final class BrowseFilter {
 public static boolean matches(CatalogItem item,String query,String group,String source,boolean foreign){
  String haystack=(item.title+" "+item.country+" "+item.category+" "+item.source).toLowerCase(Locale.ROOT);
  for(String word:query.trim().toLowerCase(Locale.ROOT).split("\\s+"))if(!haystack.contains(word))return false;
  return (group.equals("全部")||group.equals(foreign?item.country:item.category))&&(source.equals("全部来源")||source.equals(item.source));
 }
}
