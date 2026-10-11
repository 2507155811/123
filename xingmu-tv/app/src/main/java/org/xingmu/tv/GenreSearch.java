package org.xingmu.tv;
import java.util.*;
/** Match only source-supplied type metadata, never guessing genre from a movie title. */
public final class GenreSearch {
 public static boolean matches(String metadata,String query){String text=metadata==null?"":metadata.toLowerCase(Locale.ROOT);String word=query==null?"":query.trim().toLowerCase(Locale.ROOT);if(word.isEmpty())return true;String[] tokens=word.split("[\\s,，、/]+");for(String token:tokens){String[] aliases=token.equals("爱情")?new String[]{"爱情","言情","恋爱","romance"}:token.equals("科幻")?new String[]{"科幻","sci-fi","science fiction"}:token.equals("喜剧")?new String[]{"喜剧","搞笑","comedy"}:token.equals("动漫")?new String[]{"动漫","动画","anime"}:new String[]{token};boolean found=false;for(String alias:aliases)if(text.contains(alias)){found=true;break;}if(!found)return false;}return true;}
}
