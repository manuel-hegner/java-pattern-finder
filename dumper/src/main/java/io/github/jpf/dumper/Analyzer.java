package io.github.jpf.dumper;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.input.BoundedInputStream;
import org.mariadb.jdbc.Driver;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.InstanceOfExpr;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.powerlibraries.io.In;
import com.google.common.base.Joiner;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;

public class Analyzer extends VoidVisitorAdapter<Void>{
	
	private Multiset<Dump> counts = HashMultiset.create();
	
	public static void main(String[] args) throws IOException, InstantiationException, IllegalAccessException, SQLException {
		Driver.class.newInstance();
		try (Connection conn = DriverManager.getConnection("jdbc:mysql://192.168.178.78/dumper", "dumper", "dump")) {

			conn.createStatement().execute("TRUNCATE dump");
			
			for(File f:new File("results2").listFiles()) {
				if(f.getName().endsWith(".zip")) {
					Analyzer a = new Analyzer();
					System.out.println(f);
					try (ZipInputStream in = In.file(f).asZip()) {
						ZipEntry entry;
						while((entry = in.getNextEntry())!=null) {
							BoundedInputStream bin = new BoundedInputStream(in, entry.getSize());
							bin.setPropagateClose(false);
							CompilationUnit cu = JavaParser.parse(bin);
							cu.accept(a, null);
						}
					}
					a.dumpToDB(conn, f);
				}
			}
		}
	}
	
	private void dumpToDB(Connection conn, File f) throws SQLException {
		int source = Integer.parseInt(f.getName().substring(0,f.getName().length()-4));
		try (PreparedStatement stmt = conn.prepareStatement("INSERT DELAYED INTO dump VALUES (?,?,?,?)")) {// ON DUPLICATE KEY UPDATE count = count + ?;")) {
			
			for(Entry<Dump> e:counts.entrySet()) {	
				try {
					stmt.setString(1, e.getElement().getId());
					stmt.setString(2, e.getElement().getType().name());
					stmt.setInt(3, source);
					stmt.setInt(4, e.getCount());
					//stmt.setInt(5, e.getCount());
					stmt.addBatch();
				} catch(Exception ex) {
					ex.printStackTrace();
				}
			}
			stmt.executeBatch();
		}
	}
	
	@Override
	public void visit(MethodCallExpr n, Void arg) {
		super.visit(n, arg);
		if(n.getArguments().isNonEmpty()) {
			addDump(n.getNameAsString()+"||"+Joiner.on("|").join(n.getArguments()), Type.METHOD_ARGS);
		}
		
		if(n.getScope().orElse(null) instanceof MethodCallExpr) {
			LinkedList<String> l = new LinkedList<>();
			l.add(n.getNameAsString());
			MethodCallExpr scope = n;
			while(scope.getScope().orElse(null) instanceof MethodCallExpr) {
				MethodCallExpr next = (MethodCallExpr) scope.getScope().get();
				
				l.addFirst(next.getNameAsString());
				addDump(Joiner.on('|').join(l), Type.METHOD_CHAINS);
				scope = next;
			}
		}
	}
	
	@Override
	public void visit(ObjectCreationExpr n, Void arg) {
		super.visit(n, arg);
		if(n.getArguments().isNonEmpty()) {
			addDump(n.getType()+"||"+Joiner.on("|").join(n.getArguments()), Type.CONTR_ARGS);
		}
		
		if(n.getScope().orElse(null) instanceof MethodCallExpr) {
			LinkedList<String> l = new LinkedList<>();
			l.add(n.getType().toString());
			
			MethodCallExpr scope = (MethodCallExpr) n.getScope().get();
			
			l.addFirst(scope.getNameAsString());
			addDump(Joiner.on('|').join(l), Type.CONTR_METHOD_CHAINS);
			
			while(scope.getScope().orElse(null) instanceof MethodCallExpr) {
				MethodCallExpr next = (MethodCallExpr) scope.getScope().get();
				
				l.addFirst(next.getNameAsString());
				addDump(Joiner.on('|').join(l), Type.CONTR_METHOD_CHAINS);
				scope = next;
			}
		}
	}
	
	@Override
	public void visit(BinaryExpr n, Void arg) {
		super.visit(n, arg);
		if(n.getLeft() instanceof MethodCallExpr && n.getRight() instanceof MethodCallExpr) {
			addDump(((MethodCallExpr)n.getLeft()).getNameAsString()+"||"+n.getOperator()+"||"+((MethodCallExpr)n.getRight()).getNameAsString(), Type.BIN_METHODS);
		}
	}
	
	@Override
	public void visit(CastExpr n, Void arg) {
		super.visit(n, arg);
		if(n.getExpression() instanceof MethodCallExpr)
			addDump(((MethodCallExpr)n.getExpression()).getNameAsString()+"||"+n.getType().toString(), Type.CAST_METHOD);
	}
	
	@Override
	public void visit(InstanceOfExpr n, Void arg) {
		super.visit(n, arg);
		addDump(n.getType().toString(), Type.INSTANCE_OF_TYPE);
	}
	
	@Override
	public void visit(MethodDeclaration n, Void arg) {
		super.visit(n, arg);
		if(n.getBody().isPresent()) {
			BlockStmt body = n.getBody().get();
			if(body.isEmpty())
				addDump(n.getSignature().toString(), Type.EMPTY_METHOD);
			if(body.getStatements().size()==1) {
				Statement stmt = body.getStatement(0);
				if(stmt instanceof ReturnStmt && ((ReturnStmt) stmt).getExpression().orElse(null) instanceof LiteralExpr) {
					addDump(n.getSignature()+"||"+((ReturnStmt) stmt).getExpression().get(), Type.STATIC_RETURN);
				}
			}
		}
		
	}
	
	@Override
	public void visit(UnaryExpr n, Void arg) {
		super.visit(n, arg);
		if(n.getExpression() instanceof MethodCallExpr) {
			addDump(n.getOperator()+"||"+((MethodCallExpr)n.getExpression()).getNameAsString(), Type.UNARY_METHOD);
		}
	}
	
	private void addDump(String id, Type type) {
		if(id.length()<200)
			counts.add(new Dump(id, type));
	}
}
