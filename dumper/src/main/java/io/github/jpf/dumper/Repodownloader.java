package io.github.jpf.dumper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;

import com.github.powerlibraries.io.In;
import com.github.powerlibraries.io.Out;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.OkUrlFactory;

public class Repodownloader {
	
	public static void main(String[] args) throws IOException, InterruptedException {
		OkUrlFactory factory = new OkUrlFactory(new OkHttpClient());
		
		byte[] buffer = new byte[1024*1024*2];
		
		for(String l:In.file("result.txt").withUTF8().readLines()) {
			String[] parts = l.split("\t");
			System.out.println(l);
			File f = new File("results",parts[0]+".zip");
			if(f.isFile()) {
				System.out.println("\tEXISTS");
			}
			else {
				try {
					HttpURLConnection con = factory.open(new URL("https://github.com/"+parts[1]+"/archive/"+parts[2]+".zip"));
					File tmp = File.createTempFile("repo", ".zip");
					try (ZipInputStream in = In.stream(con.getInputStream()).asZip();
						ZipOutputStream zip = Out.file(tmp).asZip();) {
						
						ZipEntry entry;
						while((entry = in.getNextEntry())!=null) {
							if(entry.getName().endsWith(".java")) {
								System.out.println("\t"+entry.getName());
								zip.putNextEntry(entry);
								IOUtils.copyLarge(new BoundedInputStream(in, entry.getSize()), zip, buffer);
								zip.closeEntry();
							}
						}
						
					}
					
					con.disconnect();
					
					FileUtils.moveFile(tmp, f);
							
					System.out.println("\tDOWNLOADED");
					
					
					Thread.sleep(1000);
				} catch(Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
}
