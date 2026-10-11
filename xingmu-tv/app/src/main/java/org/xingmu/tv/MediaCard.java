package org.xingmu.tv;

import android.app.Activity;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.*;

/** A focusable card with separated artwork, title and compact metadata. */
final class MediaCard extends LinearLayout {
 private final TextView title;private final String name;private boolean favourite;
 MediaCard(Activity a,CatalogItem item,boolean fav,boolean poster){super(a);name=item.title;favourite=fav;setOrientation(VERTICAL);setBackground(TvUi.focus());setClickable(true);TvUi.focusable(this);setPadding(TvUi.dp(a,12),TvUi.dp(a,12),TvUi.dp(a,12),TvUi.dp(a,12));setClipToPadding(false);setContentDescription(item.title+"，"+item.category+"，"+item.country+"，"+item.source);
  int kind=item.opera()?3:item.section.equals("foreign")?2:item.live()?0:1;int color=TvUi.PALETTE[kind];if(poster){int[] colors={0xffa49af4,0xff62c9dc,0xffe0b976,0xffd48bb5,0xff76cdb3,0xff88acf0};color=colors[(item.id.hashCode()&0x7fffffff)%colors.length];}
  if(poster){FrameLayout art=new FrameLayout(a);art.setBackground(TvUi.gradient(0xff192943,0xff132039,TvUi.dp(a,10)));art.setClipToOutline(true);art.addView(new CinemaArt(a,kind,color,false),new FrameLayout.LayoutParams(-1,-1));TextView badge=TvUi.text(a,item.category,11,color);badge.setPadding(10,5,10,5);badge.setBackground(TvUi.box(0xcc0a1322,0));FrameLayout.LayoutParams badgeParams=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.START);badgeParams.setMargins(8,8,8,8);art.addView(badge,badgeParams);addView(art,new LinearLayout.LayoutParams(-1,TvUi.dp(a,102)));TvUi.gap(a,this,10);}
  else{LinearLayout top=TvUi.row(a);TextView channel=TvUi.text(a,item.mode.equals("official")?"官方频道":"LIVE",11,color);channel.setTypeface(null,Typeface.BOLD);top.addView(channel,new LinearLayout.LayoutParams(0,-2,1));TextView flag=TvUi.text(a,item.country,11,TvUi.MUTED);top.addView(flag);addView(top);TvUi.gap(a,this,8);}
  title=TvUi.title(a,"",poster?16:18);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);addView(title,new LinearLayout.LayoutParams(-1,0,1));updateFavourite(fav);
  TextView meta=TvUi.text(a,poster?item.country+" · "+item.category:item.category,12,TvUi.MUTED);meta.setSingleLine(true);meta.setEllipsize(TextUtils.TruncateAt.END);addView(meta);TvUi.gap(a,this,6);TextView mode=TvUi.text(a,item.mode.equals("official")?"官网观看  ↗":"直接播放  ▷",11,item.mode.equals("official")?0xffe1b98b:0xff71d7bc);addView(mode);
 }
 void updateFavourite(boolean value){favourite=value;title.setText((favourite?"★ ":"")+name);}
}
