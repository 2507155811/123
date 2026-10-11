package org.xingmu.tv;
import org.junit.Test;import static org.junit.Assert.*;
public class FilmTimeOrderTest {
 @Test public void newestAndOldestOrders(){assertTrue(FilmTimeOrder.compare("2026","2020",true)<0);assertTrue(FilmTimeOrder.compare("2026","2020",false)>0);assertEquals(0,FilmTimeOrder.compare("2025","2025",true));}
 @Test public void unknownAlwaysComesLast(){assertTrue(FilmTimeOrder.compare("未知","2026",true)>0);assertTrue(FilmTimeOrder.compare("未知","2026",false)>0);assertEquals(0,FilmTimeOrder.compare("","未知",false));}
 @Test public void sourceYearFormatsAreAccepted(){assertEquals(2026,FilmTimeOrder.year("2026-10-11"));assertEquals(2025,FilmTimeOrder.year("2025年"));assertEquals(-1,FilmTimeOrder.year("更新至26集"));}
}
