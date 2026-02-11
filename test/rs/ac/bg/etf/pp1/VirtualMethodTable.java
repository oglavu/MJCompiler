package rs.ac.bg.etf.pp1;

import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.concepts.*;

public class VirtualMethodTable {

	private static HashMap<Obj, ArrayList<Obj>> methodVT = new HashMap<Obj, ArrayList<Obj>>();
	private static HashMap<Struct, Integer> tableStart = new HashMap<Struct, Integer>();
	
	public static int ABSTRACT_METH_ADR = -1;
	
	private static void putDW(int value, int address) {
		Code.put(Code.const_);
		Code.put4(value);
		Code.put(Code.putstatic); 
		Code.put2(address);
	}
	
	public static void newTable(Obj baseObj, Obj derivedObj) {
		methodVT.put(
			derivedObj, 
			(baseObj == null) 
				? new ArrayList<Obj>()
				: (ArrayList<Obj>) methodVT.get(baseObj).clone()
		);
	}
	
	public static void putEntry(Obj classObj, Obj meth) {
		ArrayList<Obj> table = methodVT.get(classObj);
		for (int i=0; i<table.size(); ++i) {
			if (table.get(i).getName().equals( meth.getName() )) {
				table.set(i, meth);
				return;
			}
		}
		table.add(meth);
	}
	
	public static void generateCode() {
		for (HashMap.Entry<Obj, ArrayList<Obj>> entry : methodVT.entrySet()) {
			tableStart.put(entry.getKey().getType(), Code.dataSize);
			ArrayList<Obj> table = entry.getValue();
			for (int i=0; i<table.size(); ++i) {
				addMethodEntry(table.get(i).getName(), table.get(i).getAdr());
			}
		}
		addTableTerminator();
	}
	
	public static ArrayList<String> getAbsMeths(Obj classObj) {
		ArrayList<Obj> table = methodVT.get(classObj);
		ArrayList<String> absMeths = new ArrayList<String>();
		if (table == null) return absMeths;
		for (Obj meth : table)
			if (meth.getAdr() == ABSTRACT_METH_ADR)
				absMeths.add(meth.getName());
		return absMeths;
	}
	
	public static int getTableAddress(Struct s) {
		return tableStart.get(s);
	}
	
	private static void addNameTerminator() { 
		putDW(-1, Code.dataSize++); 
	}
	
	private static void addTableTerminator() { 
		putDW(-2, Code.dataSize++); 
	} 
	
	private static void addFunctionAddress(int methodAddr) { 
		putDW(methodAddr, Code.dataSize++); 
	} 
	
	private static void addMethodEntry(String methodName, int methodAdrInCodeMemory) { 
		for (int ix=0; ix<methodName.length(); ix++)
			putDW((int)(methodName.charAt(ix)), Code.dataSize++); 
		
		addNameTerminator(); 
		addFunctionAddress(methodAdrInCodeMemory); 
	}
	
	public static void printVMT(Logger log) {
		for (HashMap.Entry<Obj, ArrayList<Obj>> entry : methodVT.entrySet()) {
			int tabStart = tableStart.get(entry.getKey().getType()).intValue();
			report_info(log, entry.getKey().getName() + "\t" + tabStart, null);
			for (Obj meth : entry.getValue()) {
				report_info(log, "\t" + meth.getName() + " " + meth.getAdr(), null);
			}
		}
	}
	
	public static void report_info(Logger log, String message, SyntaxNode info) {
		StringBuilder msg = new StringBuilder(message); 
		int line = (info == null) ? 0: info.getLine();
		if (line != 0)
			msg.append (" na liniji ").append(line);
		log.info(msg.toString());
	}

}