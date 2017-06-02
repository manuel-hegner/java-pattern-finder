package io.github.jpf.dumper;

import java.io.Serializable;
import java.util.HashMap;

import com.google.common.collect.Multiset;

import lombok.Data;

@Data
public class BigDump implements Serializable {

	private String id;
	private int type;
	private Multiset<Integer> counts;
	
	public void add(int source, Dump element, int count) {
		counts.add(source, count);
	}

}
