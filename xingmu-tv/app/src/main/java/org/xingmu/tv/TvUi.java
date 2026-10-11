package org.xingmu.tv;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.widget.*;

final class TvUi {
 static final int BG=0xff080d18,INK=0xfff1f5fc,MUTED=0xff8898b0,ACCENT=0xff82b4ff;
 static final int[] PALETTE={0xff6db9ff,0xffaa91ff,0xff55d9bb,0xffffbb82};
 static int dp(Activity a,int n){return (int)(a.getResources().getDisplayMetrics().density*n+.5f);}
 static boolean wide(Activity a){return a.getResources().getConfiguration().screenWidthDp>=840;}
 static LinearLayout col(Activity a){LinearLayout v=new LinearLayout(a);v.setOrientation(LinearLayout.VERTICAL);v.setClipChildren(false);v.setClipToPadding(false);return v;}
 static LinearLayout row(Activity a){LinearLayout v=new LinearLayout(a);v.setGravity(android.view.Gravity.CENTER_VERTICAL);return v;}
 static TextView text(Activity a,String value,int size,int color){TextView t=new TextView(a);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");return t;}
 static TextView title(Activity a,String value,int size){TextView t=text(a,value,size,INK);t.setTypeface(null,Typeface.BOLD);return t;}
 static GradientDrawable box(int color,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(14);if(stroke!=0)d.setStroke(2,stroke);return d;}
 static GradientDrawable gradient(int start,int end,int radius){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{start,end});d.setCornerRadius(radius);return d;}
 static StateListDrawable focus(){StateListDrawable d=new StateListDrawable();d.addState(new int[]{android.R.attr.state_focused},box(0xff263c5c,ACCENT));d.addState(new int[]{android.R.attr.state_pressed},box(0xff263c5c,ACCENT));d.addState(new int[]{},box(0xff162235,0));return d;}
 static void focusable(View v){v.setFocusable(true);v.setOnFocusChangeListener((view,focused)->{view.animate().scaleX(focused?1.025f:1f).scaleY(focused?1.025f:1f).setDuration(130).start();view.setElevation(focused?12f:0f);});}
 static Button button(Activity a,String label){Button b=new Button(a);b.setText(label);b.setAllCaps(false);b.setTextColor(INK);b.setTextSize(15);b.setMinHeight(dp(a,44));b.setMinimumHeight(dp(a,44));b.setPadding(dp(a,18),dp(a,10),dp(a,18),dp(a,10));b.setBackground(focus());focusable(b);return b;}
 static void styleSearch(Activity a,EditText v){v.setTextColor(INK);v.setHintTextColor(MUTED);v.setTextSize(16);v.setBackground(box(0xff111c2d,0xff27344a));v.setPadding(dp(a,18),dp(a,10),dp(a,18),dp(a,10));}
 static void full(Activity a){a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);}
 static LinearLayout root(Activity a){full(a);LinearLayout v=col(a);int padding=wide(a)?36:a.getResources().getConfiguration().screenWidthDp>=600?26:18;v.setPadding(dp(a,padding),dp(a,20),dp(a,padding),dp(a,18));v.setBackgroundColor(BG);a.setContentView(v);return v;}
 static void gap(Activity a,LinearLayout root,int size){root.addView(new View(a),new LinearLayout.LayoutParams(1,dp(a,size)));}
 static void toast(Activity a,String s){Toast.makeText(a,s,Toast.LENGTH_LONG).show();}
}
