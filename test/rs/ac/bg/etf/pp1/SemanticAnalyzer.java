package rs.ac.bg.etf.pp1;

import java.util.Stack;

import org.apache.log4j.Logger;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class SemanticAnalyzer extends VisitorAdaptor {
	
	private boolean errorDetected = false;
	Logger log = Logger.getLogger(getClass());
	private int constant, nxtEnumVal, nVars, formParamCnt;
	private Struct constantType,
		currentType,
		boolType = Tab.find("bool").getType();
	private Obj currentEnum,
		mainMethod, 
		currentMethod,
		currentProgam;
	private boolean returnHappend;
	
	public int getnVars() {
		return nVars;
	}

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
	public void visit(MethodName methodName) {
		methodName.obj = Tab.insert(Obj.Meth, methodName.getI1() , currentType);
		if (methodName.getI1().equals("main")) {
			if (currentType != Tab.noType) {
				currentMethod = methodName.obj = Tab.noObj;
				report_error("Main metoda mora imati povratni tip void.", methodName);
			} else {
				mainMethod = methodName.obj;
			}
		}
		currentMethod = methodName.obj;
		Tab.openScope();
	}
	
	@Override
	public void visit(FormParam_var formParam_var) {
		
		if (currentMethod == Tab.noObj) {
			report_error("Formalan parametar neadekavatnog metoda: " + formParam_var.getI2(), formParam_var);
			return;
		}
		
		Obj varObj = Tab.currentScope().findSymbol(formParam_var.getI2());
		
		if(varObj == null || varObj == Tab.noObj) {
			varObj = Tab.insert(Obj.Var, formParam_var.getI2(), currentType);
			varObj.setFpPos(++this.formParamCnt);
			currentMethod.setLevel(currentMethod.getLevel()+1);
		}
		else{
			report_error("Dvostruka definicija formalnog parametra: " + formParam_var.getI2(), formParam_var);
		}
	}
	
	@Override
	public void visit(FormParam_arr formParam_arr) {
		if (currentMethod == Tab.noObj) {
			report_error("Formalan parametar neadekavatnog metoda: " + formParam_arr.getI2(), formParam_arr);
			return;
		}
		
		Obj varObj = Tab.currentScope().findSymbol(formParam_arr.getI2());
		
		if(varObj == null || varObj == Tab.noObj) {
			varObj = Tab.insert(Obj.Var, formParam_arr.getI2(), new Struct(Struct.Array, currentType));
			varObj.setFpPos(++this.formParamCnt);
			currentMethod.setLevel(currentMethod.getLevel()+1);
		}
		else{
			report_error("Dvostruka definicija formalnog parametra: " + formParam_arr.getI2(), formParam_arr);
		}
	}
	
	@Override
	public void visit(MethodDecl methodDecl) {
		nVars = Tab.currentScope().getnVars();
		Tab.chainLocalSymbols(currentMethod);
		Tab.closeScope();
		
		// TODO: Proveriti da li se dogadja return za svaki moguci slucaj
		returnHappend = false;
		formParamCnt = 0;
	}
	
	private class Pair {
		public Obj obj;
		public int fp_ix;
		
		Pair(Obj o, int i) {
			this.obj = o;
			this.fp_ix = i;
		}
	}
	
	Stack<Pair> methodCalls = new Stack<Pair>();
	
	@Override
	public void visit(MethodInvokeName methodInvokeName) {
		Obj obj = Tab.find(methodInvokeName.getI1());
		if (obj == null || obj == Tab.noObj) {
			report_error("Poziv nedefinisane metode: "+methodInvokeName.getI1(), methodInvokeName);
		}
		methodInvokeName.obj = obj;
		methodCalls.push(new Pair(obj, 1)); // fp krecu od 1
	}
	
	@Override
	public void visit(ActParam actParam) {
		if (methodCalls.empty()) {
			report_error("Nerazresen problem sa imenom metode", actParam);
			return;
		}
		
		Struct apStruct = actParam.getExpr().struct;
		Pair method = methodCalls.peek();
		
		if (apStruct == Tab.noType) {
			report_error("Neadekvatan tip stvarnog parametra na poziciji " + method.fp_ix, actParam);
		} else {
			for (Obj fp : method.obj.getLocalSymbols()) {
				if (fp.getFpPos() == method.fp_ix) {
					if (fp.getType().getKind() == apStruct.getKind()) {
						if (apStruct.getKind() == Struct.Array && apStruct.getElemType() != fp.getType().getElemType()) {
							report_error("Neadekvatan tip elemenata niza stvarnog parametra " + fp.getName() + " metode " + method.obj.getName(), actParam);
						}
					}
					else {
						report_info("got:" + apStruct.getKind() + ", expected " +fp.getType().getKind(),null);
						report_error("Neadekvatan tip stvarnog parametra " + fp.getName() + " metode " + method.obj.getName(), actParam);
					}
					method.fp_ix++;
					break;
				}
			}
		}
		
	}
	
	/* CONST DECLARATIONS */
	@Override
	public void visit(ConstAssign constAssign) {
		Obj conObj = Tab.find(constAssign.getI1());
		if(conObj != Tab.noObj) {
			report_error("Dvostruka definicija konstante: " + constAssign.getI1(), constAssign);
		}
		else {
			if(constantType.assignableTo(currentType)) {
				conObj = Tab.insert(Obj.Con, constAssign.getI1(), currentType);
				conObj.setAdr(constant);
			}
			else {
				report_error("Neadekvatna dodela konstanti: " + constAssign.getI1(), constAssign);
			}
		}
	}
	
	@Override
	public void visit(Literal_n literal_n) {
		constant = literal_n.getN1();
		constantType = literal_n.struct = Tab.intType;
	}
	
	@Override
	public void visit(Literal_c literal_c) {
		constant = literal_c.getC1();
		constantType = literal_c.struct = Tab.charType;
	}
	
	@Override
	public void visit(Literal_b literal_b) {
		constant = literal_b.getB1();
		constantType = literal_b.struct = boolType;
	}
	
	/* VAR DECLARATIONS */
	@Override
	public void visit(Var_var var_var) {
		Obj varObj = null;
		varObj = Tab.currentScope().findSymbol(var_var.getI1());

		if(varObj == null || varObj == Tab.noObj) {
			varObj = Tab.insert(Obj.Var, var_var.getI1(), currentType);
		}
		else{
			report_error("Dvostruka definicija promenljiva: " + var_var.getI1(), var_var);
		}
	}
	
	@Override
	public void visit(Var_arr var_arr) {
		Obj varObj = null;
		varObj = Tab.currentScope().findSymbol(var_arr.getI1());
		
		if(varObj == null || varObj == Tab.noObj) {
			varObj = Tab.insert(Obj.Var, var_arr.getI1(), new Struct(Struct.Array, currentType));
		}
		else{
			report_error("Dvostruka definicija promenljiva: " + var_arr.getI1(), var_arr);
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
		else {
			currentType = typeObj.getType();	
		}
		type.struct = currentType;
	}
	
	@Override
	public void visit(MethodRet_void methodRet_void) {
		currentType = Tab.noType;
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
				designator_dot.obj = new Obj(Obj.Con, "length", Tab.intType);
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
		Struct exprStruct = designator_arr.getExpr().struct;
		if(arrObj == Tab.noObj) {
			report_error("Pristup nedefinisanoj promenljivi niza: " + designator_arr.getDesignator(), designator_arr);
			designator_arr.obj = Tab.noObj;
		}
		else if(arrObj.getType().getKind() == Struct.Array) {
			if (exprStruct == Tab.noType) {
				report_error("Neadekvatan indeks niza: " + designator_arr.getDesignator(), designator_arr);
				designator_arr.obj = Tab.noObj;
			} 
			else if (exprStruct == Tab.intType) {
				designator_arr.obj = new Obj(Obj.Elem, arrObj.getName() + ".element", arrObj.getType().getElemType());
			} 
			else {
				report_error("Neadekvatan tip indeksa niza: " + designator_arr.getDesignator(), designator_arr);
				designator_arr.obj = Tab.noObj;
			}
		}
		else {
			report_error("Neadekvatna promenljiva niza: " + designator_arr.getDesignator(), designator_arr);
			designator_arr.obj = Tab.noObj;
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
	public void visit(FactorSub_l factorSub_l) {
		factorSub_l.struct = factorSub_l.getLiteral().struct;
	}
	
	@Override
	public void visit(FactorSub_meth factorSub_meth) {
		if(factorSub_meth.getMethodInvokeName().obj.getKind() != Obj.Meth) {
			report_error("Ime " + factorSub_meth.getMethodInvokeName().getI1() + " nije naziv metode.", factorSub_meth);
			factorSub_meth.struct = Tab.noType;
		} 
		else if (methodCalls.isEmpty()) {
			report_error("Nedefinisana greska sa imenom metode: " + factorSub_meth.getMethodInvokeName().obj.getName(), factorSub_meth);
			factorSub_meth.struct = Tab.noType;
		} 
		else {
			Pair method = methodCalls.pop();
			int fp_ix = method.fp_ix > 0 ? method.fp_ix-1 : 0;
			if (fp_ix > method.obj.getLevel()
				|| fp_ix < method.obj.getLevel()
			) {
				report_error("Broj stvarnih parametara (" + fp_ix + ") razlicit od ocekivanog (" + method.obj.getLevel() + ").", factorSub_meth);
				factorSub_meth.struct = Tab.noType;
			}
			else {
				factorSub_meth.struct = factorSub_meth.getMethodInvokeName().obj.getType();
			}
		}
	}
	@Override 
	public void visit(FactorSub_var factorSub_var) {
		Obj designatorObj = factorSub_var.getDesignator().obj;
		if (designatorObj.getType().getKind() == Struct.Enum) {
			report_info("Implicitna konverzija tipa enum u int: " + designatorObj.getName(), factorSub_var);
			factorSub_var.struct = Tab.intType;
		}
		else {
			factorSub_var.struct = designatorObj.getType();
		}
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
		
		if(left.equals(Tab.intType) || left.getKind() == Struct.Enum 
				&& right.equals(Tab.intType) || left.getKind() == Struct.Enum)
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
	
	//Ternary
	public void visit(Expr_ternary expr_ternary) {
		Struct trueStruct = expr_ternary.getCondTrueExpr().struct,
			falseStruct = expr_ternary.getCondFalseExpr().struct,
			condStruct = expr_ternary.getCondFact().struct;
		if (!condStruct.equals(boolType)) {
			report_error("Uslov mora biti bool", expr_ternary);
			expr_ternary.struct = Tab.noType;
		} else if (trueStruct.equals(Tab.noType)) {
			report_error("TrueExpr ne sme biti noType", expr_ternary);
			expr_ternary.struct = Tab.noType;
		} else if (falseStruct.equals(Tab.noType)) {
			report_error("FalseExpr ne sme biti noType", expr_ternary);
			expr_ternary.struct = Tab.noType;
		} else if (falseStruct.equals(trueStruct)) {
			expr_ternary.struct = falseStruct;
		} else {
			report_error("TrueExpr i FalseExpr moraju biti istog tipa", expr_ternary);
			expr_ternary.struct = falseStruct;
		}
	}
	
	@Override
	public void visit(CondTrueExpr condTrueExpr) {
		condTrueExpr.struct = condTrueExpr.getExpr().struct;
	}
	
	@Override
	public void visit(CondFalseExpr condFalseExpr) {
		condFalseExpr.struct = condFalseExpr.getExpr().struct;
	}
	
	@Override
	public void visit(CondFactNoRelop condFactNoRelop) {
		condFactNoRelop.struct = condFactNoRelop.getExprNoTernary().getAddopTermList().struct;
	}
	
	@Override
	public void visit(CondFactRelop condFactRelop) {
		Struct expr1Struct = condFactRelop.getExprNoTernary().getAddopTermList().struct,
			expr2Struct = condFactRelop.getExprNoTernary1().getAddopTermList().struct;
		if (expr1Struct.compatibleWith(expr2Struct)) {
			condFactRelop.struct = boolType;
		} else {
			condFactRelop.struct = Tab.noType;
		}
	}
	
	@Override
	public void visit(ForCondNoRelop forCondNoRelop) {
		forCondNoRelop.struct = forCondNoRelop.getExprNoTernary().getAddopTermList().struct;
	}
	
	@Override
	public void visit(ForCondRelop forCondRelop) {
		Struct expr1Struct = forCondRelop.getExprNoTernary().getAddopTermList().struct,
			expr2Struct = forCondRelop.getExprNoTernary1().getAddopTermList().struct;
		if (expr1Struct.compatibleWith(expr2Struct)) {
			forCondRelop.struct = boolType;
		} else {
			forCondRelop.struct = Tab.noType;
		}
	}
	
	//Designator Statements
	@Override
	public void visit(DesignatorStatement_assign designatorStatement_assign) {
		int kind = designatorStatement_assign.getDesignator().obj.getKind();
		Struct desStruct = designatorStatement_assign.getDesignator().obj.getType(),
				exprStruct = designatorStatement_assign.getExpr().struct;
		// TODO: Implementiraj proveru da li je vrednost Expr validna za dati Enum
		if(kind != Obj.Var && kind != Obj.Elem && kind != Obj.Fld) 
			report_error("Dodela u neadekvatnu promenljivu: " + designatorStatement_assign.getDesignator().obj.getName(), designatorStatement_assign);
		else if(exprStruct != desStruct
				&& (desStruct.getKind() == Struct.Enum && exprStruct.getKind() != Struct.Int))
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
	
	@Override
	public void visit(DesignatorStatement_meth designatorStatement_meth) {
		if(designatorStatement_meth.getMethodInvokeName().obj.getKind() != Obj.Meth) {
			report_error("Ime " + designatorStatement_meth.getMethodInvokeName().obj.getName() + " nije naziv metode.", designatorStatement_meth);
		}
	}
	
	// Statement
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
	
	@Override
	public void visit(Statement_if statement_if) {
		if (statement_if.getCondFact().struct == Tab.noType) {
			report_error("Uslov IFa je neadekvatnog tipa.", statement_if);
		}
	}
	
	
}
