package rs.ac.bg.etf.pp1;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
		
		if (args.length != 1) {
			log.error("Ime izvornog fajla nije specificirano.");
			return;
		}
		
		Path sourcePath = Paths.get(args[0]);
		if (!Files.exists(sourcePath)) {
			log.error("Zadati fajl ne postoji.");
			return;
		}
		
		Reader br = null;
		try {
			File sourceCode = new File(args[0]);
			log.info("Compiling source file: " + sourceCode.getAbsolutePath());
			
			br = new BufferedReader(new FileReader(sourceCode));
			Yylex lexer = new Yylex(br);
			
			/* Formiranje AST */
			MJParser p = new MJParser(lexer);
	        Symbol s = p.parse();
	        
	        Program prog = (Program)(s.value);
	        
			/* Ispis AST */
	        if (lexer.errorDetected) {
	        	log.error("Lekser ne prihvata sekvencu.");
	        	return;
	        } else {
	        	log.info(prog.toString(""));
	        }
	        
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
			Code.dataSize += sa.getnVars();
			
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
			File objFile = new File("test/"+sourcePath.getFileName().toString()+".obj");
			if(objFile.exists()) objFile.delete();
			
			CodeGenerator cg = new CodeGenerator();
			prog.traverseBottomUp(cg);
			Code.mainPc = cg.getMainPc();
			Code.write(new FileOutputStream(objFile));
			
			VirtualMethodTable.printVMT(sa.log);
			
			log.info("Kod izgenerisan.");
			
		} 
		finally {
			if (br != null) try { br.close(); } catch (IOException e1) { log.error(e1.getMessage(), e1); }
		}

	}
	
	
}
