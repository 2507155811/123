package org.xingmu.tv;
import org.junit.Test;import static org.junit.Assert.*;
public class BrowseFilterTest {
 private final CatalogItem item=new CatalogItem("nhk","NHK World","foreign","新闻","日本","https://example.org/live.m3u8","stream","公开来源","已授权");
 @Test public void caseInsensitiveSearchAcrossFields(){assertTrue(BrowseFilter.matches(item,"nhk 日本","全部","全部来源",true));}
 @Test public void allQueryWordsMustMatch(){assertFalse(BrowseFilter.matches(item,"nhk 美国","全部","全部来源",true));}
 @Test public void exactCountryAndSourceFiltersCombine(){assertTrue(BrowseFilter.matches(item,"","日本","公开来源",true));assertFalse(BrowseFilter.matches(item,"","美国","公开来源",true));assertFalse(BrowseFilter.matches(item,"","日本","另一个来源",true));}
 @Test public void genreFilteringUsesCategory(){assertTrue(BrowseFilter.matches(item,"新闻","新闻","全部来源",false));assertFalse(BrowseFilter.matches(item,"","日本","全部来源",false));}
 @Test public void whitespaceOnlyQueryMatches(){assertTrue(BrowseFilter.matches(item,"  \t ","全部","全部来源",true));}
}
