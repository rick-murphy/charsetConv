package com.rick.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Iterator;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.FalseFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.mozilla.universalchardet.CharsetListener;
import org.mozilla.universalchardet.UniversalDetector;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class CharsetConvertor {
	
	private class EncodingListener implements CharsetListener{
		String encoding = null;
		public void report(String charset) {
			encoding = charset;
		}
	}
	
	private class EncodingFileFilter implements IOFileFilter{
		private String[] fp = new String[0];
		public EncodingFileFilter(String[] filePatterns){
			fp = filePatterns;
		}
		public boolean accept(File pathname) {
			if(ArrayUtils.isEmpty(fp))
				return true;
			for(String p : fp)
				if(FilenameUtils.wildcardMatchOnSystem(pathname.getName(), p))
					return true;
			return false;
		}
		public boolean accept(File dir, String name) {
			return true;
		}
	}
	
	private ContentHandler ch = new BodyContentHandler();
	private AutoDetectParser psr = new AutoDetectParser();
	private UniversalDetector detector = new UniversalDetector(null);
	
	/* Command Line
	 * -b --base:	work directory
	 * -f --file:	file names seperated by comma, support wildcard match
	 * -o --output:	output directory. must not be "base"
	 * -r --recursive:	deal directory recursive
	 * -s --source-charset: source charset
	 * -t --target-charset: target charset
	 * -h --help
	 * */

	public static void main(String[] args) {
		Options opt = new Options();
		opt.addOption("h", "help", false, "print this help page.");
		opt.addOption("r", "recursive", false, "include subdirectory recursively.");
		Object[][] params = new Object[][]{
			{"b", "base", "Base directory in which search files to convert.", Boolean.TRUE},
			{"f", "file", "file filters seperated by comma, wildcard supported.", Boolean.FALSE},
			{"o", "output", "Output directory, convert files will place in this directory with structure. If none, the source files will be overwritten.", Boolean.FALSE},
			{"s", "source-charset", "Limit convert scope within files with given charset", Boolean.FALSE},
			{"t", "target-charset", "Base directory in which search files to convert", Boolean.TRUE}
		};
		for(Object[] conf : params){
			OptionBuilder.withLongOpt(conf[1].toString());
			OptionBuilder.withDescription(conf[2].toString());
			OptionBuilder.withValueSeparator('=');
//			OptionBuilder.hasArg(((Boolean)conf[3]).booleanValue());
			OptionBuilder.hasArg();
			opt.addOption(OptionBuilder.create(conf[0].toString()));
		}
		String helpStr = "java -jar charsetConv.jar (-b/--base)(-t/--target-charset)[-f/--file][-o/--output][-s/--source-charset][-h/--help]";
		// parse arguments
		HelpFormatter formatter = new HelpFormatter();
        CommandLineParser parser = new PosixParser();
        CommandLine cl = null;
        try {
            cl = parser.parse( opt, args );
        } catch (ParseException e) {
            formatter.printHelp( helpStr, opt );
        }
        //
        if(cl.hasOption("h")){
        	formatter.printHelp(helpStr,"", opt, "");
        	return;
        }
        boolean recursive = cl.hasOption("r");
        String base = cl.getOptionValue("b");
        String file = cl.getOptionValue("f");
        String output = cl.getOptionValue("o");
        String sCharset = cl.getOptionValue("s");
        String tCharset = cl.getOptionValue("t");
        if(StringUtils.isEmpty(base)){
        	System.out.println("must indicate a base directory");
        	return;
        }
        if(StringUtils.isEmpty(tCharset)){
        	System.out.println("must indicate target charset");
        	return;
        }
        if(base.equalsIgnoreCase(output)){
        	System.out.println("base directory and output directory can not be same");
        	return;
        }
        if(tCharset.equalsIgnoreCase(sCharset)){
        	System.out.println("Are you sure you DO NEED to do this?");
        	return;
        }
//		File f = new File("d:\\test.txt");
		CharsetConvertor cc = new CharsetConvertor();
		try {
			cc.doConvert(new File(base), file, new File(output), sCharset, tCharset, recursive);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void doConvert(File base, String fileNames, File output, String sourceCharset, String targetCharset, boolean isRecursive) throws IOException, SAXException, TikaException{
		/* is write to another directory */
		boolean op = (null != output);
		if(op){
			if(output.exists() && output.isDirectory()){
				FileUtils.cleanDirectory(output);
			}else{
				output.mkdirs();
			}
		}
		/* is strict limit to original encodings */
		boolean sc = !StringUtils.isEmpty(sourceCharset);
		/* find all files by name pattern */
		IOFileFilter dirFilter = isRecursive ? TrueFileFilter.INSTANCE : FalseFileFilter.INSTANCE;
		String[] names = StringUtils.isEmpty(fileNames) ? new String[0] : fileNames.split(",");
		EncodingFileFilter filter = new EncodingFileFilter(names);
		Collection<File> files = FileUtils.listFiles(base, filter, dirFilter);
		// start convert
		for(Iterator<File> it = files.iterator(); it.hasNext();){
			File f = it.next();
			String type = getFileType(f);
			if(StringUtils.isEmpty(type))
				continue;
			String oriCharset = guessFileCharset(f);
			if(sc && !oriCharset.equals(sourceCharset))
				continue;
			System.out.println(MessageFormat.format("{0}({1} -- {2}) --> ({3})", 
				f.getAbsolutePath(), oriCharset, type, targetCharset));
			if(oriCharset.equalsIgnoreCase(targetCharset)){
				//when source file and target file are both the same encoding, just do copy
				if(op){
					FileUtils.copyFile(f, new File(f.getAbsolutePath().replace(base.getAbsolutePath(), output.getAbsolutePath())));
				}
			}else{
				File tgt = op ? new File(f.getAbsolutePath().replace(base.getAbsolutePath(), output.getAbsolutePath())) : f;
				String content = IOUtils.toString(new FileInputStream(f), oriCharset);
				FileUtils.write(tgt, content, targetCharset);
			}
			
		}
	}
	
	private String getFileType(File file) throws FileNotFoundException, IOException, SAXException, TikaException{
		/*if(!Charset.isSupported(encoding))
			return;*/
		Metadata meta = getFileMeta(file);
		String type = meta.get(Metadata.CONTENT_TYPE);
		if(!type.startsWith("text/"))
			return null;
		return type;
	}
	
	private String guessFileCharset(File file) throws IOException{
		EncodingListener el = new EncodingListener();
//		UniversalDetector detector = new UniversalDetector(el);
		detector.reset();
		detector.setListener(el);
		FileInputStream fis = new FileInputStream(file);
		byte[] buf = new byte[4096];
		int nread = 0;
		while((nread = fis.read(buf)) > 0 && !detector.isDone()){
			detector.handleData(buf, 0, nread);
		}
		detector.dataEnd();
		fis.close();
		String cs = el.encoding;
		if(StringUtils.isEmpty(cs))
			// The probe result may be null if no special charactor apear, like ANSI. Treat this as UTF-8
			cs = "UTF-8";	
		return cs;
	}
	
	private Metadata getFileMeta(File file) throws IOException, SAXException, TikaException{
		FileInputStream fis = new FileInputStream(file);
		Metadata meta = new Metadata();
		meta.set(Metadata.RESOURCE_NAME_KEY, file.getName());
		psr.parse(fis, ch, meta);
		fis.close();
		return meta;
	}
}
