package org.xingmu.tv;
import java.util.*;
public final class FilmCategory {
 public static final List<String> OPTIONS=Collections.unmodifiableList(Arrays.asList("全部","电影","电视剧","动漫","综艺","纪录片","戏曲","其他"));
 public static String of(String value){String s=value==null?"":value.toLowerCase(Locale.ROOT);if(s.matches(".*(戏曲|京剧|豫剧|越剧|黄梅戏|昆曲|粤剧|评剧|秦腔).*"))return "戏曲";if(s.matches(".*(纪录|记录|documentary).*"))return "纪录片";if(s.matches(".*(动漫|动画|anime|cartoon).*"))return "动漫";if(s.matches(".*(综艺|晚会|真人秀|variety).*"))return "综艺";if(s.matches(".*(电视剧|连续剧|剧集|短剧|国产剧|欧美剧|日剧|韩剧|日韩剧|港台剧|香港剧|台湾剧|海外剧|泰剧|美剧|英剧|series).*"))return "电视剧";if(s.matches(".*(电影|片|movie|film).*"))return "电影";return "其他";}
 public static boolean matches(String category,String selected){return "全部".equals(selected)||of(category).equals(selected);}
}
