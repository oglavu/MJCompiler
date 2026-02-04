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
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class Compiler {

	static {
		DOMConfigurator.configure(Log4JUtils.instance().findLoggerConfigFile());
		Log4JUtils.instance().prepareLogFile(Logger.getRootLogger());
	}
	
	public static void main(String[] args) throws Exception {
		
		Logger log = Logger.getLogger(Compiler.class);
		
		Reader br = null;
		try {
			File sourceCode = new File("test/test301.mj");
			log.info("Compiling source file: " + sourceCode.getAbsolutePath());
			
			br = new BufferedReader(new FileReader(sourceCode));
			Yylex lexer = new Yylex(br);
			
			/* Formiranje AST */
			MJParser p = new MJParser(lexer);
	        Symbol s = p.parse();
	        
	        Program prog = (Program)(s.value);
	        
			/* Ispis AST */
			log.info(prog.toString(""));
			
			if(!p.errorDetected){
				log.info("Parsiranje uspesno zavrseno!");
			}else{
				log.error("Parsiranje NIJE uspesno zavrseno!");
				return;
			}
			log.info("=====================================================================");
			
			
			/* Semanticka analiza */
			/* Inicijalizacija tabele simbola */
			Tab.init();
			Struct boolType = new Struct(Struct.Bool);
			Obj boolObj = Tab.insert(Obj.Type, "bool", boolType);
			boolObj.setAdr(-1);
			boolObj.setLevel(-1);
			
			SemanticAnalyzer sa = new SemanticAnalyzer();
			prog.traverseBottomUp(sa);
			
			/* Ispis tabele simbola */
			log.info("=====================================================================");
			Tab.dump();
			
			if (sa.passed()) {
				log.info("Kod semanticki ispravan!");
			} else {
				log.info("Kod semanticki NEispravan!");
				return;
			}
			
			/* Generisanje koda */
			File objFile = new File("test/program.obj");
			if(objFile.exists()) objFile.delete();
			
			CodeGenerator cg = new CodeGenerator();
			prog.traverseBottomUp(cg);
			Code.dataSize = sa.getnVars();
			Code.mainPc = cg.getMainPc();
			Code.write(new FileOutputStream(objFile));
			
			log.info("Kod izgenerisan.");
			
		} 
		finally {
			if (br != null) try { br.close(); } catch (IOException e1) { log.error(e1.getMessage(), e1); }
		}

	}
	
	
}
