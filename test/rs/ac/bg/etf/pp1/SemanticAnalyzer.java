package rs.ac.bg.etf.pp1;

import java.util.HashSet;

import org.apache.log4j.Logger;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class SemanticAnalyzer extends VisitorAdaptor {
	
	private boolean errorDetected = false;
	Logger log = Logger.getLogger(getClass());
	private int constant, nxtEnumVal;
	private Struct constantType,
		currentType,
		boolType = Tab.find("bool").getType();
	private Obj currentEnum,
		mainMethod, 
		currentProgam;
	private boolean returnHappend;

	/* LOG MESSAGES */
	public void report_error(String message, SyntaxNode info) {
		errorDetected  = true;
		StringBuilder msg = new StringBuilder(message);
		int line = (info == null) ? 0: info.getLine();
		if (line != 0)
			msg.append (" na liniji ").append(line);
		log.error(msg.toString());
	}

	public void report_info(String message, SyntaxNode info) {
		StringBuilder msg = new StringBuilder(message); 
		int line = (info == null) ? 0: info.getLine();
		if (line != 0)
			msg.append (" na liniji ").append(line);
		log.info(msg.toString());
	}
	
	public boolean passed() {
		return !errorDetected;
	}
	
	
	/* SEMANTIC PASS CODE */

	@Override
	public void visit(ProgramName programName) {
		currentProgam = Tab.insert(Obj.Prog, programName.getI1(), Tab.noType);
		Tab.openScope();
	}
	
	@Override
	public void visit(Program program) {
		Tab.chainLocalSymbols(currentProgam);
		Tab.closeScope();
		currentProgam = null;
		
		if(mainMethod == null || mainMethod.getLevel() > 0)
			report_error("Program nema adekvatnu main metodu", program);
	}
	
	@Override
	public void visit(MainName mainName) {
		this.mainMethod = Tab.insert(Obj.Meth, "main", Tab.noType);
		Tab.openScope();
	}
	
	@Override
	public void visit(MainMethod mainMethod) {
		Tab.chainLocalSymbols(this.mainMethod);
		Tab.closeScope();
		
		returnHappend = false;
	}
	
	/* CONST DECLARATIONS */
	@Override
	public void visit(ConDecl conDecl) {
		Obj conObj = Tab.find(conDecl.getI1());
		if(conObj != Tab.noObj) {
			report_error("Dvostruka definicija konstante: " + conDecl.getI1(), conDecl);
		}
		else {
			if(constantType.assignableTo(currentType)) {
				conObj = Tab.insert(Obj.Con, conDecl.getI1(), currentType);
				conObj.setAdr(constant);
			}
			else {
				report_error("Neadekvatna dodela konstanti: " + conDecl.getI1(), conDecl);
			}
		}
	}
	
	@Override
	public void visit(Constant_n constant_n) {
		constant = constant_n.getN1();
		constantType = Tab.intType;
	}
	
	@Override
	public void visit(Constant_c constant_c) {
		constant = constant_c.getC1();
		constantType = Tab.charType;
	}
	
	@Override
	public void visit(Constant_b constant_b) {
		constant = constant_b.getB1();
		constantType = boolType;
	}
	
	/* VAR DECLARATIONS */
	@Override
	public void visit(VarDecl_var varDecl_var) {
		Obj varObj = null;
		varObj = Tab.currentScope().findSymbol(varDecl_var.getI1());

		if(varObj == null || varObj == Tab.noObj) {
			varObj = Tab.insert(Obj.Var, varDecl_var.getI1(), currentType);
		}
		else{
			report_error("Dvostruka definicija promenljiva: " + varDecl_var.getI1(), varDecl_var);
		}
	}
	
	@Override
	public void visit(VarDecl_array varDecl_array) {
		Obj varObj = null;
		varObj = Tab.currentScope().findSymbol(varDecl_array.getI1());
		
		if(varObj == null || varObj == Tab.noObj) {
			varObj = Tab.insert(Obj.Var, varDecl_array.getI1(), new Struct(Struct.Array, currentType));
		}
		else{
			report_error("Dvostruka definicija promenljiva: " + varDecl_array.getI1(), varDecl_array);
		}
	}
	
	@Override
	public void visit(Type type) {
		Obj typeObj = Tab.find(type.getI1());
		if(typeObj == Tab.noObj) {
			report_error("Nepostojeci tip podatka: " + type.getI1(), type);
			currentType = Tab.noType;
		}
		else if(typeObj.getKind() != Obj.Type) {
			report_error("Neadekvatan tip podatka: " + type.getI1(), type);
			currentType = Tab.noType;
		}
		else
			currentType = typeObj.getType();
	}
	
	//Designator
	@Override
	public void visit(Designator_simple designator_simple) {
		Obj obj = Tab.find(designator_simple.getI1());
		if(obj == Tab.noObj) {
			report_error("Pristup nedefinisanoj promenljivi: " + designator_simple.getI1(), designator_simple);
			designator_simple.obj = Tab.noObj;
		}
		else if(obj.getKind() != Obj.Var && obj.getKind() != Obj.Con && obj.getKind() != Obj.Meth && obj.getKind() != Obj.Type) {
			report_error("Neadekvatna promenljiva: " + designator_simple.getI1(), designator_simple);
			designator_simple.obj = Tab.noObj;
		}
		else {
			designator_simple.obj = obj;
		}
	}
	
	public void visit(Designator_dot designator_dot) {
		Obj obj = designator_dot.getDesignator().obj;
		String designatorName = obj.getName();
		String memberName = designator_dot.getI2();
		boolean found = false;
		if (obj.getType().getKind() == Struct.Enum) {
			for (Obj sym : obj.getType().getMembers()) {
				if (sym.getName().contentEquals(memberName)) {
					found = true;
					designator_dot.obj = sym;
					break;
				}
			}
			if (!found) {
				report_error("Ime " + memberName + " nije clan nabrajanja" + designatorName, designator_dot);
				designator_dot.obj = Tab.noObj;
			}
		} else if (obj.getType().getKind() == Struct.Array) {
			if (memberName.equals("length")) {
				designator_dot.obj = Tab.lenObj;
			} else {
				report_error("Ime " + designatorName + " je tipa niz. Ne sadrzi clan " + memberName, designator_dot);
				designator_dot.obj = Tab.noObj;
			}
		} else {
			report_error("Nedefinisano ime: " + designatorName, designator_dot);
			designator_dot.obj = Tab.noObj;
		}
	}
	
	@Override
	public void visit(Designator_arr designator_arr) {
		Obj arrObj = Tab.find(designator_arr.getDesignator().obj.getName());
		if(arrObj == Tab.noObj) {
			report_error("Pristup nedefinisanoj promenljivi niza: " + designator_arr.getDesignator(), designator_arr);
			designator_arr.obj = Tab.noObj;
		}
		else if(arrObj.getKind() != Obj.Var || arrObj.getType().getKind() != Struct.Array) {
			report_error("Neadekvatna promenljiva niza: " + designator_arr.getDesignator(), designator_arr);
			designator_arr.obj = Tab.noObj;
		}
		else {
			designator_arr.obj = arrObj;
		}
	}
	
	//Enum
	public void visit(EnumName enumName) {
		Obj obj = Tab.currentScope().findSymbol(enumName.getI1());
		if (obj != null) {
			report_error("Dvostruka definicija nabrajanja " + enumName.getI1(), enumName);
		} else {
			this.currentEnum = Tab.insert(Obj.Type, enumName.getI1(), new Struct(Struct.Enum));
			this.nxtEnumVal = 0;
			Tab.openScope();
		}
	}
	
	private boolean enumHasVal(Integer val) {
		for (Obj valObj : Tab.currentScope().values()) {
			if (valObj.getAdr() == val) {
				return true;
			}
		}
		return false;
	}
	
	public void visit(EnumElemExplicit enumElemExplicit) {
		Obj enumObj = Tab.currentScope().findSymbol(enumElemExplicit.getI1());
		if (enumObj != null) {
			report_error("Dvostruka definicija nabrajanja: " + enumElemExplicit.getI1(), enumElemExplicit);
		} else {
			if (enumHasVal(enumElemExplicit.getN2())) {
				report_error("Vrednost vec dodeljena drugom clanu nabrajanja: "+enumElemExplicit.getI1(), enumElemExplicit);
			}
			Obj enumElemObj = Tab.insert(Obj.Con, enumElemExplicit.getI1(), Tab.intType);
			enumElemObj.setAdr(enumElemExplicit.getN2());
			this.nxtEnumVal = enumElemExplicit.getN2() + 1;
		}
	}
	
	public void visit(EnumElemImplicit enumElemImplicit) {
		Obj enumObj = Tab.currentScope().findSymbol(enumElemImplicit.getI1());
		if (enumObj != null) {
			report_error("Dvostruka definicija nabrajanja: " + enumElemImplicit.getI1(), enumElemImplicit);
		} else {
			if (enumHasVal(this.nxtEnumVal)) {
				report_error("Vrednost vec dodeljena drugom clanu nabrajanja: "+enumElemImplicit.getI1(), enumElemImplicit);
				
			}
			Obj currentEnumConst = Tab.insert(Obj.Con, enumElemImplicit.getI1(), Tab.intType);
			currentEnumConst.setAdr(this.nxtEnumVal);
			++this.nxtEnumVal;
		}
	}
	
	public void visit(EnumDecl enumDecl) {
		currentEnum.getType().setMembers(Tab.currentScope().getLocals());
		Tab.closeScope();
		currentEnum = null;
	}
	
	//FactorSub
	@Override
	public void visit(FactorSub_n factorSub_n) {
		factorSub_n.struct = Tab.intType;
	}
	
	@Override
	public void visit(FactorSub_c factorSub_c) {
		factorSub_c.struct = Tab.charType;
	}
	
	@Override
	public void visit(FactorSub_b factorSub_b) {
		factorSub_b.struct = boolType;
	}
	
	@Override 
	public void visit(FactorSub_var factorSub_var) {
		factorSub_var.struct = factorSub_var.getDesignator().obj.getType();
	}
	
	@Override
	public void visit(FactorSub_new_array factorSub_new_array) {
		if(!factorSub_new_array.getExpr().struct.equals(Tab.intType)) {
			report_error("Velicina niza nije int tipa.", factorSub_new_array);
			factorSub_new_array.struct = Tab.noType;
		}
		else
			factorSub_new_array.struct = new Struct(Struct.Array, currentType);
			
	}
	
	@Override
	public void visit(FactorSub_expr factorSub_expr) {
		factorSub_expr.struct = factorSub_expr.getExpr().struct;
	}
	
	//Factor
	@Override
	public void visit(Factor factor) {
		if(factor.getUnary() instanceof Unary_m) {
			if(factor.getFactorSub().struct.equals(Tab.intType))
				factor.struct = Tab.intType;
			else {
				report_error("Negacija ne int vrednosti", factor);
				factor.struct = Tab.noType;
			}
		}
		else
			factor.struct = factor.getFactorSub().struct;
	}
	
	//Expr
	@Override
	public void visit(MulopFactorList_factor mulopFactorList_factor) {
		mulopFactorList_factor.struct = mulopFactorList_factor.getFactor().struct;
	}
	
	@Override
	public void visit(MulopFactorList_mul mulopFactorList_mul) {
		Struct left = mulopFactorList_mul.getMulopFactorList().struct;
		Struct right = mulopFactorList_mul.getFactor().struct;
		if(left.equals(Tab.intType) && right.equals(Tab.intType))
			mulopFactorList_mul.struct = Tab.intType;
		else {
			report_error("Mulop operacija ne int vrednosti.", mulopFactorList_mul);
			mulopFactorList_mul.struct = Tab.noType;
		}
	}
	
	@Override
	public void visit(Term term) {
		term.struct = term.getMulopFactorList().struct;
	}
	
	@Override
	public void visit(AddopTermList_term addopTermList_term) {
		addopTermList_term.struct = addopTermList_term.getTerm().struct;
	}
	
	@Override
	public void visit(AddopTermList_add addopTermList_add) {
		Struct left = addopTermList_add.getAddopTermList().struct;
		Struct right = addopTermList_add.getTerm().struct;
		if(left.equals(Tab.intType) && right.equals(Tab.intType))
			addopTermList_add.struct = Tab.intType;
		else {
			report_error("Addop operacija ne int vrednosti.", addopTermList_add);
			addopTermList_add.struct = Tab.noType;
		}
	}
	
	@Override
	public void visit(Expr_noternary expr_noternary) {
		expr_noternary.struct = expr_noternary.getExprNoTernary().getAddopTermList().struct;
	}
	
	//Designator Statements
	@Override
	public void visit(DesignatorStatement_assign designatorStatement_assign) {
		int kind = designatorStatement_assign.getDesignator().obj.getKind();
		if(kind != Obj.Var && kind != Obj.Elem && kind != Obj.Fld) 
			report_error("Dodela u neadekvatnu promenljivu: " + designatorStatement_assign.getDesignator().obj.getName(), designatorStatement_assign);
		else if(!designatorStatement_assign.getExpr().struct.assignableTo(designatorStatement_assign.getDesignator().obj.getType()))
			report_error("Neadekvatna dodela vrednosti u promenljivu: " + designatorStatement_assign.getDesignator().obj.getName(), designatorStatement_assign);
	}
	
	@Override
	public void visit(DesignatorStatement_inc designatorStatement_inc) {
		int kind = designatorStatement_inc.getDesignator().obj.getKind();
		if(kind != Obj.Var && kind != Obj.Elem && kind != Obj.Fld) 
			report_error("Inkrement neadekvatne promenljive: " + designatorStatement_inc.getDesignator().obj.getName(), designatorStatement_inc);
		else if(!designatorStatement_inc.getDesignator().obj.getType().equals(Tab.intType))
			report_error("Inkrement ne int promenljive: " + designatorStatement_inc.getDesignator().obj.getName(), designatorStatement_inc);
	}
	
	@Override
	public void visit(DesignatorStatement_dec designatorStatement_dec) {
		int kind = designatorStatement_dec.getDesignator().obj.getKind();
		if(kind != Obj.Var && kind != Obj.Elem && kind != Obj.Fld) 
			report_error("Dekrement neadekvatne promenljive: " + designatorStatement_dec.getDesignator().obj.getName(), designatorStatement_dec);
		else if(!designatorStatement_dec.getDesignator().obj.getType().equals(Tab.intType))
			report_error("Dekrement ne int promenljive: " + designatorStatement_dec.getDesignator().obj.getName(), designatorStatement_dec);
	}
	
	//Single Statements
	
	@Override
	public void visit(Statement_read singleStatement_read) {
		int kind = singleStatement_read.getDesignator().obj.getKind();
		Struct type = singleStatement_read.getDesignator().obj.getType();
		if(kind != Obj.Var && kind != Obj.Elem && kind != Obj.Fld)
			report_error("Read operacija neadekvatne promenljive: " + singleStatement_read.getDesignator().obj.getName(), singleStatement_read);
		else if(!type.equals(Tab.intType) && !type.equals(Tab.charType) && !type.equals(boolType))
			report_error("Read operacija ne int/char/bool promenljive: " + singleStatement_read.getDesignator().obj.getName(), singleStatement_read);
	}
	
	@Override
	public void visit(Statement_print1 singleStatement_print1) {
		Struct type = singleStatement_print1.getExpr().struct;
		if(!type.equals(Tab.intType) && !type.equals(Tab.charType) && !type.equals(boolType))
			report_error("Print operacija ne int/char/bool izraza", singleStatement_print1);
	}
	
	@Override
	public void visit(Statement_print2 singleStatement_print2) {
		Struct type = singleStatement_print2.getExpr().struct;
		if(!type.equals(Tab.intType) && !type.equals(Tab.charType) && !type.equals(boolType))
			report_error("Print operacija ne int/char/bool izraza", singleStatement_print2);
	}
	
	@Override
	public void visit(Statement_return1 singleStatement_return1) {
		returnHappend = true;
	}
	
	@Override
	public void visit(Statement_return2 singleStatement_return2) {
		returnHappend = true;
	}
	
	
}
