package io.github.jpf.dumper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;

@Data @AllArgsConstructor
public class Dump {
	@NonNull
	private String id;
	@NonNull
	private Type type;
}
