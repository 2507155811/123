package org.xingmu.tv;
import java.util.*;
public final class TvChannelGroup {
 public static final List<String> OPTIONS=Collections.unmodifiableList(Arrays.asList("我的收藏","央视频道","卫视频道","电影频道","电视剧","少儿频道","地方频道","综艺频道","纪录频道","戏曲频道","国外频道","其他频道"));
 public static String of(CatalogItem item){String text=item.title+" "+item.category;if(item.section.equals("foreign"))return "国外频道";if(text.matches(".*(戏曲|京剧|豫剧|越剧|黄梅戏|昆曲|粤剧|评剧|秦腔).*"))return "戏曲频道";if(text.toUpperCase(Locale.ROOT).matches(".*(CCTV|央视).*"))return "央视频道";if(text.contains("卫视"))return "卫视频道";if(text.matches(".*(少儿|儿童|卡通|动漫|动画).*"))return "少儿频道";if(text.matches(".*(纪录|记录|探索|地理).*"))return "纪录频道";if(text.matches(".*(电影|影视轮播).*"))return "电影频道";if(text.matches(".*(电视剧|剧场|剧集).*"))return "电视剧";if(text.matches(".*(综艺|音乐|晚会).*"))return "综艺频道";if(text.matches(".*(地方|本地|省市|城市).*"))return "地方频道";return "其他频道";}
}
