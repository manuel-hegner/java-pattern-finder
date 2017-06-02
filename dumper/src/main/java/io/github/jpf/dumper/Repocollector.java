package io.github.jpf.dumper;

import java.io.BufferedWriter;
import java.io.IOException;

import org.kohsuke.github.GHDirection;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHRepositorySearchBuilder.Sort;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterator;
import org.kohsuke.github.PagedSearchIterable;
import org.kohsuke.github.extras.OkHttpConnector;

import com.github.powerlibraries.io.Out;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.OkUrlFactory;

public class Repocollector {
	
	public static void main(String[] args) throws IOException {
		GitHub github = GitHub
			.connectAnonymously();
		github.setConnector(new OkHttpConnector(new OkUrlFactory(new OkHttpClient())));
		
		PagedSearchIterable<GHRepository> res = github
			.searchRepositories()
			.q("NOT android")
			.language("java")
			.stars(">100")
			.sort(Sort.STARS)
			.order(GHDirection.DESC)
			.list();
		PagedIterator<GHRepository> it = res.iterator();
		
		try(BufferedWriter out=Out.file("result.txt").withUTF8().asWriter()) {
			while(it.hasNext()) {
				for(GHRepository rep:it.nextPage()) {
					String str = 
						 rep.getId()+"\t"
						+rep.getFullName()+"\t"
						+rep.getDefaultBranch()+"\t"
						+rep.getStargazersCount()+"\t"
						+rep.getSize()+"\n";
					
					
					System.out.println(str);
					out.write(str);
				}
				
				try {
					Thread.sleep(8000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
}
