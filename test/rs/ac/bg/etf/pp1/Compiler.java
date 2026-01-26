package rs.ac.bg.etf.pp1;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

import java_cup.runtime.Symbol;

import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import rs.ac.bg.etf.pp1.ast.*;
import rs.ac.bg.etf.pp1.util.Log4JUtils;

public class Compiler {

	static {
		DOMConfigurator.configure(Log4JUtils.instance().findLoggerConfigFile());
		Log4JUtils.instance().prepareLogFile(Logger.getRootLogger());
	}
	
	public static void main(String[] args) throws Exception {
		
		Logger log = Logger.getLogger(Compiler.class);
		
		Reader br = null;
		try {
			File sourceCode = new File("test/program.mj");
			log.info("Compiling source file: " + sourceCode.getAbsolutePath());
			
			br = new BufferedReader(new FileReader(sourceCode));
			Yylex lexer = new Yylex(br);
			
			/* Formiranje AST */
			MJParser p = new MJParser(lexer);
	        Symbol s = p.parse();
	        
	        Program prog = (Program)(s.value);
	        
			/* Ispis AST */
			log.info(prog.toString(""));
			log.info("=====================================================================");
			
			/* Inicijalizacija tabele simbola */
			//Tab.init();
			//Struct boolType = new Struct(Struct.Bool);
			//Obj boolObj = Tab.insert(Obj.Type, "bool", boolType);
			//boolObj.setAdr(-1);
			//boolObj.setLevel(-1);
			
			/* Semanticka analiza */
			//SemAnalyzer sa = new SemAnalyzer();
			//prog.traverseBottomUp(sa);
			
			/* Ispis tabele simbola */
			//log.info("=====================================================================");
			//Tab.dump();
			
			if(!p.errorDetected /*&& sa.passed()*/){
				/* Generisanje koda */
				//File objFile = new File("test/program.obj");
				//if(objFile.exists()) objFile.delete();
				
				//CodeGenerator cg = new CodeGenerator();
				//prog.traverseBottomUp(cg);
				//Code.dataSize = sa.nVars;
				//Code.mainPc = cg.getmainPc();
				//Code.write(new FileOutputStream(objFile));
				
				log.info("Generisanje uspesno zavrseno!");
			}else{
				log.error("Parsiranje NIJE uspesno zavrseno!");
			}
			
		} 
		finally {
			if (br != null) try { br.close(); } catch (IOException e1) { log.error(e1.getMessage(), e1); }
		}

	}
	
	
}
