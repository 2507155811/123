package org.xingmu.tv;
import android.app.Activity;import android.graphics.Color;import android.graphics.drawable.*;import android.content.res.ColorStateList;import android.view.*;import android.widget.*;
final class TvUi {
 static final int BG=0xff0b1021,INK=0xfff4f6ff,MUTED=0xffa4afc9,ACCENT=0xffa0abff;
 static int dp(Activity a,int n){return (int)(a.getResources().getDisplayMetrics().density*n+.5f);}
 static LinearLayout col(Activity a){LinearLayout v=new LinearLayout(a);v.setOrientation(LinearLayout.VERTICAL);return v;}
 static LinearLayout row(Activity a){LinearLayout v=new LinearLayout(a);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
 static TextView text(Activity a,String value,int size,int color){TextView t=new TextView(a);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
 static GradientDrawable box(int color,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(18);if(stroke!=0)d.setStroke(3,stroke);return d;}
 static StateListDrawable focus(){StateListDrawable d=new StateListDrawable();d.addState(new int[]{android.R.attr.state_focused},box(0xff354474,ACCENT));d.addState(new int[]{android.R.attr.state_pressed},box(0xff354474,ACCENT));d.addState(new int[]{},box(0xff19233d,0));return d;}
 static Button button(Activity a,String label){Button b=new Button(a);b.setText(label);b.setAllCaps(false);b.setTextColor(INK);b.setTextSize(17);b.setPadding(dp(a,16),dp(a,12),dp(a,16),dp(a,12));b.setBackground(focus());b.setFocusable(true);return b;}
 static void full(Activity a){a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);}
 static LinearLayout root(Activity a){full(a);LinearLayout v=col(a);int padding=a.getResources().getConfiguration().smallestScreenWidthDp<600?16:42;v.setPadding(dp(a,padding),dp(a,20),dp(a,padding),dp(a,20));v.setBackgroundColor(BG);a.setContentView(v);return v;}
 static void gap(Activity a,LinearLayout root,int dp){root.addView(new View(a),new LinearLayout.LayoutParams(1,TvUi.dp(a,dp)));}
 static void toast(Activity a,String s){Toast.makeText(a,s,Toast.LENGTH_LONG).show();}
}
