package charsetConv;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Test;
import org.mozilla.universalchardet.CharsetListener;
import org.mozilla.universalchardet.UniversalDetector;

public class TestConv {
	private class EncodingListener implements CharsetListener{
		String encoding = null;
		public void report(String charset) {
			encoding = charset;
		}
	}
	@Test
	public void test() {
		File f = new File("d:\\spy.log");
		EncodingListener el = new EncodingListener();
		UniversalDetector detector = new UniversalDetector(el);
        
        byte[] buf = new byte[4096];
        try {
			java.io.FileInputStream fis = new java.io.FileInputStream(f);
			
			int nread;
			while ((nread = fis.read(buf)) > 0 && !detector.isDone()) {
			    detector.handleData(buf, 0, nread);
			}
			detector.dataEnd();
			System.out.println(el.encoding);
			fis.close();
		} catch (Exception e) {
			fail(e.getMessage());
		}
        if(!detector.isDone())
        	fail("not guess out");
	}

}
