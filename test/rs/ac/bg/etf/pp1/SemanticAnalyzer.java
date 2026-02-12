package rs.ac.bg.etf.pp1;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;

import org.apache.log4j.Logger;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class SemanticAnalyzer extends VisitorAdaptor {
	
	private boolean errorDetected = false;
	Logger log = Logger.getLogger(getClass());
	private int constant, nxtEnumVal, nVars, formParamCnt, fieldCnt;
	private Struct constantType,
		currentType,
		boolType = Tab.find("bool").getType();
	private Obj currentClass,
		currentEnum,
		mainMethod, 
		currentMethod,
		currentProgam;
	private boolean returnHappend;
	private ArrayList<Obj> abstractClasses = new ArrayList<Obj>();

	public int getnVars() {
		return nVars;
	}
	
	public SemanticAnalyzer() {
		initDefaultMeth();
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
		nVars = Tab.currentScope().getnVars();
		Tab.chainLocalSymbols(currentProgam);
		Tab.closeScope();
		currentProgam = null;
		
		if(mainMethod == null || mainMethod.getLevel() > 0)
			report_error("Program nema adekvatnu main metodu", program);
	}
	
	@Override
	public void visit(MethodName methodName) {
		Obj obj = Tab.currentScope().findSymbol(methodName.getI1());
		if (obj != null) {
			report_error("Dvostruka definicija metode " + methodName.getI1() + " unutar klase " + currentClass.getName(), methodName);
			this.currentMethod = methodName.obj = Tab.noObj;
			Tab.openScope();
			return;
		}
		
		obj = Tab.insert(Obj.Meth, methodName.getI1() , currentType);
		obj.setLevel(0);
		if (methodName.getI1().equals("main")) {
			if (currentType != Tab.noType) {
				obj = Tab.noObj;
				report_error("Main metoda mora imati povratni tip void.", methodName);
			} else {
				this.mainMethod = obj;
			}
		}
		
		if (this.currentClass != null) {
			VirtualMethodTable.putEntry(currentClass, obj);
		}
		this.currentMethod = methodName.obj = obj;
		Tab.openScope();
	}
	
	@Override
	public void visit(AbsMethodName absMethodName) {
		
		if (abstractClasses.contains(currentClass)) {
			report_error("Apstraktna metoda " + absMethodName.getI1() + " unutar konkretne klase " + currentClass.getName(), absMethodName);
		}
		
		Obj obj = Tab.currentScope().findSymbol(absMethodName.getI1());
		if (obj != null) {
			report_error("Dvostruka definicija apstraktne metode " + absMethodName.getI1() + " unutar klase " + currentClass.getName(), absMethodName);
			absMethodName.obj = Tab.noObj;
			return;
		}
		
		obj = Tab.insert(Obj.Meth, absMethodName.getI1() , currentType);
		obj.setAdr(VirtualMethodTable.ABSTRACT_METH_ADR); // apstraktna
		obj.setLevel(0);
		
		VirtualMethodTable.putEntry(currentClass, obj);
		
		this.currentMethod = absMethodName.obj = obj;
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
		Tab.chainLocalSymbols(this.currentMethod);
		Tab.closeScope();
		
		this.currentMethod = null;
		this.returnHappend = false;
		this.formParamCnt = 0;
	}
	
	@Override
	public void visit(ClassMethodDecl_abs classMethodDecl_abs) {
		Tab.chainLocalSymbols(this.currentMethod);
		Tab.closeScope();
		
		this.currentMethod = null;
		this.currentType = null;
		this.formParamCnt = 0;
	}
	
	@Override
	public void visit(ClassName className) {
		Obj obj = Tab.find(className.getI1());
		if (obj != Tab.noObj) {
			report_error("Dvostruka definicija tipa " + className.getI1(), className);
		} else {
			this.currentClass = Tab.insert(Obj.Type, className.getI1(), new Struct(Struct.Class));
			Tab.openScope();
			Obj fld = Tab.insert(Obj.Fld, "__vmtp__", Tab.intType);
			fld.setAdr(this.fieldCnt++);
			fld.setLevel(1);
		}
	}
	
	@Override
	public void visit(ExtendsAtr_ext extendsAtr_ext) {
		if (currentClass == null || currentClass == Tab.noObj) {
			report_error("Neadekvatno izvodjenje klase " + currentClass.getName(), extendsAtr_ext);
			return;
		}
		
		Obj parentObj = Tab.find(extendsAtr_ext.getType().getI1());
		
		if (parentObj == null || parentObj == Tab.noObj) {
			report_error("Nedefinisan tip osnovne klase za izvedenu klasu " + currentClass.getName(), extendsAtr_ext);
			return;
		}
		
		this.currentClass.getType().setElementType(currentType);
		VirtualMethodTable.newTable(parentObj, this.currentClass);
		
		// iz nekog razloga ne radi: this.currentClass.getType().setMembers(parentObj.getType().getMembersTable());
		for (Obj parentFld : parentObj.getType().getMembers()) {
			if (parentFld.getKind() == Obj.Meth) break;
			
			Obj inheritedFld = Tab.insert(
				parentFld.getKind(), 
				parentFld.getName(), 
				parentFld.getType()
			);
			inheritedFld.setAdr(inheritedFld.getAdr());
			inheritedFld.setLevel(1);
		}
		this.fieldCnt = parentObj.getType().getNumberOfFields();
	}
	
	@Override
	public void visit(ExtendsAtr_e extendsAtr_e) {
		VirtualMethodTable.newTable(null, this.currentClass);
	}
	
	@Override
	public void visit(ClassDecl classDecl) {
		if (classDecl.getAbstractAtr() instanceof AbstractAtr_abs) {
			 this.abstractClasses.add(currentClass);
		} else { // klasa nije deklarisana kao apstraktna
			ArrayList<String> absMeths = VirtualMethodTable.getAbsMeths(currentClass);
			if (!absMeths.isEmpty()) { // ima apstraktne metode
				for (String str : absMeths)
					report_error("Apstraktna metoda " + str + " u konkretnoj klasi " +currentClass.getName(), classDecl);
			}
		}
		
		Tab.chainLocalSymbols(currentClass.getType());
		Tab.closeScope();
		
		currentClass = null;
		fieldCnt = 0;
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
		Obj d = methodInvokeName.getDesignator().obj;
		Obj obj = null;
		
		if (d == null || d == Tab.noObj) {
			report_error("Neadekvatan poziv metode.", methodInvokeName);
			methodInvokeName.obj = Tab.noObj;
			return;
		}
		
		if (d.getKind() == Obj.Meth) {
			// from designator_simple
			// class method
			obj = d;
		} else {
			// global method
			obj = Tab.currentScope().findSymbol(d.getName());
		}
		
		
		if (obj == null || obj == Tab.noObj) {
			report_error("Poziv nedefinisane metode: "+d.getName(), methodInvokeName);
			obj = Tab.noObj;
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
		 Obj obj = Tab.currentScope().findSymbol(var_var.getI1());

		if(obj == null || obj == Tab.noObj) {
			if (currentMethod != null) {
				obj = Tab.insert(Obj.Var, var_var.getI1(), currentType);
			} else {
				if (currentClass != null) {
					for (Obj o : this.currentClass.getType().getMembers()) {
						report_info(o.getName() + " " + o.getAdr(), var_var);
					}
					obj = Tab.insert(Obj.Fld, var_var.getI1(), currentType);
					obj.setAdr(this.fieldCnt++);
					obj.setLevel(1);
				} else {
					obj = Tab.insert(Obj.Var, var_var.getI1(), currentType);
				}
				
			}
		}
		else{
			report_error("Dvostruka definicija promenljiva: " + var_var.getI1(), var_var);
		}
	}
	
	@Override
	public void visit(Var_arr var_arr) {
		Obj obj = Tab.currentScope().findSymbol(var_arr.getI1());
		
		if(obj == null || obj == Tab.noObj) {
			if (currentMethod != null) {
				obj = Tab.insert(Obj.Var, var_arr.getI1(), new Struct(Struct.Array, currentType));
			} else {
				if (currentClass != null) {
					obj = Tab.insert(Obj.Fld, var_arr.getI1(), new Struct(Struct.Array, currentType));
					obj.setAdr(this.fieldCnt++);
					obj.setLevel(1);
				} else {
					obj = Tab.insert(Obj.Var, var_arr.getI1(), new Struct(Struct.Array, currentType));
				}
			}
		
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
	
	private Obj searchLocals(Struct scopeHolder, String targetName) {
		for (Obj sym : scopeHolder.getMembers()) {
			if (sym.getName().equals(targetName)) {
				return sym;
			}
		}
		return null;
	}
	
	//Designator
	@Override
	public void visit(Designator_var designator_var) {
		Obj obj = Tab.find(designator_var.getI1());
		if(obj == Tab.noObj || obj == null) {
			report_error("Pristup nedefinisanoj varijabli: " + designator_var.getI1(), designator_var);
			obj = Tab.noObj;
		}
		else if(obj.getKind() != Obj.Var		// Ovo je simbol 'od jedne reci'
				&& obj.getKind() != Obj.Con 	// te ne moze biti Obj.Elem ili Obj.Fld
				&& obj.getKind() != Obj.Meth
			) {
			report_error("Neadekvatna varijabla: " + designator_var.getI1(), designator_var);
			obj = Tab.noObj;
		}
		
		designator_var.obj = obj;
	}
	
	@Override
	public void visit(DsgArrayName dsgArrayName) {
		Obj obj = Tab.find(dsgArrayName.getI1());
		if(obj == Tab.noObj || obj == null) {
			report_error("Pristup nedefinisanoj nizovskoj varijabli: " + dsgArrayName.getI1(), dsgArrayName);
			obj = Tab.noObj;
		}
		else if(obj.getKind() != Obj.Var 						// Nema const nizova!
				|| obj.getType().getKind() != Struct.Array) {
			report_error("Neadekvatna varijabla niza: " + dsgArrayName.getI1(), dsgArrayName);
			obj = Tab.noObj;
		}
		
		dsgArrayName.obj = obj;
	}
	
	@Override
	public void visit(DsgScopeArrayName dsgScopeArrayName) { }
	
	@Override
	public void visit(Designator_elem designator_elem) {
		Obj obj = designator_elem.getDsgArrayName().obj;
		if(obj == Tab.noObj || obj == null) {
			report_error("Nevalidan tip niza " + obj.getName(), designator_elem);
			obj = Tab.noObj;
		} else if(!designator_elem.getExpr().struct.equals(Tab.intType)
				&& designator_elem.getExpr().struct.getKind() != Struct.Enum) {
			report_error("Indeks niza nije tipa int/enum.", designator_elem);
			obj = Tab.noObj;
		} else {
			designator_elem.getDsgArrayName().obj = obj;
			obj = new Obj(Obj.Elem, obj.getName() + ".element", obj.getType().getElemType());
		}
		designator_elem.obj = obj;
	}
	
	@Override
	public void visit(Designator_scope designator_scope) {
		designator_scope.obj = designator_scope.getDsgScopeMore().obj;
	}
	
	@Override
	public void visit(Designator_scope_elem designator_scope_elem) {
		designator_scope_elem.obj = designator_scope_elem.getDsgScopeMore().obj;
	}
	
	@Override
	public void visit(DsgScopeName dsgScopeName) {
		Obj obj = Tab.find(dsgScopeName.getI1());
		if(obj == Tab.noObj || obj == null) {
			report_error("Pristup nedefinisanom tipu klase: " + dsgScopeName.getI1(), dsgScopeName);
			obj = Tab.noObj;
		} else if(obj.getKind() != Obj.Var 
				&& obj.getType().getKind() != Struct.Class
				&& obj.getType().getKind() != Struct.Enum) {
			report_error("Neadekvatna varijabla klase: " + dsgScopeName.getI1(), dsgScopeName);
			obj = Tab.noObj;
		}
		
		dsgScopeName.obj = obj;
		currentType = obj.getType();
	}
	
	@Override
	public void visit(DsgScopeElem dsgScopeElem) {
		Obj obj = dsgScopeElem.getDsgArrayName().obj;
		if(obj == Tab.noObj || obj == null) {
			obj = Tab.noObj;
		} else if(dsgScopeElem.getExpr().struct != Tab.intType
				&& dsgScopeElem.getExpr().struct.getKind() != Struct.Enum) {
			report_error("Indeks niza nije tipa int/enum.", dsgScopeElem);
			obj = Tab.noObj;
		} else {
			dsgScopeElem.getDsgArrayName().obj = obj;
			obj =  new Obj(Obj.Elem, obj.getName() + ".element", obj.getType().getElemType());			
		}
		
		dsgScopeElem.obj = obj;
		this.currentType = obj.getType();
	}
	
	@Override
	public void visit(DsgScopeMore_var dsgScopeMore_var) {
		Struct scope = this.currentType;
		Obj obj = null;
		if(scope == Tab.noType)
			obj = Tab.noObj;
		else {
			String fieldName = dsgScopeMore_var.getI1();
			obj = this.searchLocals(scope, fieldName);
			if (obj == null) {
				if (fieldName.equals("length")) {
					obj = new Obj(Obj.Con, "arr.length", Tab.intType);
				} else {
					report_error("Polje " + fieldName + " nije definisano za prilozen tip." , dsgScopeMore_var);
					obj = Tab.noObj;
				}
			}
		}
		dsgScopeMore_var.obj = obj;
		this.currentType = null;
	}
	
	@Override
	public void visit(DsgScopeMore_elem dsgScopeMore_elem) {
		Struct scope = this.currentType;
		Obj obj = null;
		if(scope == Tab.noType)
			obj = Tab.noObj;
		else if(!dsgScopeMore_elem.getExpr().struct.equals(Tab.intType)
				&& dsgScopeMore_elem.getExpr().struct.getKind() != Struct.Enum) {
			report_error("Indeks niza nije tipa int/enum.", dsgScopeMore_elem);
			obj = Tab.noObj;
		} else {
			String fieldName = dsgScopeMore_elem.getDsgScopeArrayName().getI1();
			obj = this.searchLocals(scope, fieldName);
			if (obj == null) {
				report_error("Neadekvatan pristup polju tipa niz: " + fieldName, dsgScopeMore_elem);
				obj = Tab.noObj;
			} else {
				dsgScopeMore_elem.getDsgScopeArrayName().obj = obj;
				obj = new Obj(Obj.Elem, obj.getName() + ".element", obj.getType().getElemType());
			}
		}
		
		dsgScopeMore_elem.obj = obj;
		this.currentType = null;
	}
	
	@Override
	public void visit(DsgScopeMore_scope_var dsgScopeMore_scope_var) {
		Struct scope = dsgScopeMore_scope_var.getDsgScopeMore().obj.getType();
		Obj obj = null;
		if(scope == Tab.noType)
			obj = Tab.noObj;
		else {
			String fieldName = dsgScopeMore_scope_var.getI2();
			obj = this.searchLocals(scope, fieldName);
			if (obj == null) {
				if (fieldName.equals("length")) {
					obj = new Obj(Obj.Con, "arr.length", Tab.intType);
				} else {
					report_error("Neadekvatan pristup polju: " + fieldName, dsgScopeMore_scope_var);
					obj = Tab.noObj;
				}
			}
		}
		dsgScopeMore_scope_var.obj = obj;
	}
	
	@Override
	public void visit(DsgScopeMore_scope_elem dsgScopeMore_scope_elem) {
		Struct scope = dsgScopeMore_scope_elem.getDsgScopeMore().obj.getType();
		Obj obj = null;
		if(scope == Tab.noType)
			obj = Tab.noObj;
		else if(!dsgScopeMore_scope_elem.getExpr().struct.equals(Tab.intType)
				&& dsgScopeMore_scope_elem.getExpr().struct.getKind() != Struct.Enum
					) {
			report_error("Indeks niza nije tipa int/enum.", dsgScopeMore_scope_elem);
			obj = Tab.noObj;
		}
		else {
			String fieldName = dsgScopeMore_scope_elem.getDsgScopeArrayName().getI1();
			obj = this.searchLocals(scope, fieldName);
			if (obj == null) {
				report_error("Neadekvatan pristup polju tipa niz: " + fieldName, dsgScopeMore_scope_elem);
				dsgScopeMore_scope_elem.obj = Tab.noObj;
			} else {
				dsgScopeMore_scope_elem.getDsgScopeArrayName().obj = obj;
				obj = new Obj(Obj.Elem, obj.getName() + ".element", obj.getType().getElemType());
			}
		}
		dsgScopeMore_scope_elem.obj = obj;
		this.currentType = obj.getType();
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
			Obj enumElemObj = Tab.insert(Obj.Con, enumElemExplicit.getI1(), currentEnum.getType());
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
			Obj currentEnumConst = Tab.insert(Obj.Con, enumElemImplicit.getI1(), currentEnum.getType());
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
		if(factorSub_meth.getMethodInvokeName().obj == Tab.noObj) {
			report_error("Ime " + factorSub_meth.getMethodInvokeName().getDesignator().obj.getName() + " nije naziv metode.", factorSub_meth);
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
	public void visit(FactorSub_new factorSub_new) {
		Obj obj = Tab.find(factorSub_new.getType().getI1());
		if (obj != Tab.noObj) {
			if (obj.getName().equals("int")
					|| obj.getName().equals("bool")
					|| obj.getName().equals("char")
			) {
				report_error("Operator new se ne koristi za osnovne tipove int/char/bool.", factorSub_new);
				factorSub_new.struct = Tab.noType;
			} else if (abstractClasses.contains(obj)){
				report_error("Konstrukcija apstraktnog tipa: " + obj.getName(), factorSub_new);
				factorSub_new.struct = Tab.noType;
			} else {
				factorSub_new.struct = obj.getType();
			}
		} else {
			report_error("Nepostojeci tip dodele.", factorSub_new);
			factorSub_new.struct = Tab.noType;
		}
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
			else if (factor.getFactorSub().struct.getKind() == Struct.Enum) {
				report_info("Implicitna konverzija tipa enum u int.", factor);
				factor.struct = Tab.intType;
			} else {
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
		
		if (!left.equals(Tab.noType) && left.getKind() == Struct.Enum) {
			report_info("Implicitna konverzija levog faktora iz tipa enum u int.", mulopFactorList_mul);
			left = Tab.intType;
		}
		if (!right.equals(Tab.noType) && right.getKind() == Struct.Enum) {
			report_info("Implicitna konverzija desnog faktora iz tipa enum u int.", mulopFactorList_mul);
			right = Tab.intType;
		}
		
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
		
		if (!left.equals(Tab.noType) && left.getKind() == Struct.Enum) {
			report_info("Implicitna konverzija levog terma iz tipa enum u int.", addopTermList_add);
			left = Tab.intType;
		}
		if (!right.equals(Tab.noType) && right.getKind() == Struct.Enum) {
			report_info("Implicitna konverzija desnog terma iz tipa enum u int.", addopTermList_add);
			right = Tab.intType;
		}
		
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
	
	//Ternary
	public void visit(Expr_ternary expr_ternary) {
		Struct trueStruct = expr_ternary.getCondTrueExpr().struct,
			falseStruct = expr_ternary.getCondFalseExpr().struct,
			condStruct = expr_ternary.getCondition().struct;
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
	public void visit(CondFact_expr condFact_expr) {
		condFact_expr.struct = condFact_expr.getExprNoTernary().getAddopTermList().struct;
	}
	
	@Override
	public void visit(CondFact_relop condFact_relop) {
		Struct expr1Struct = condFact_relop.getExprNoTernary().getAddopTermList().struct,
			expr2Struct = condFact_relop.getExprNoTernary1().getAddopTermList().struct;
		if (expr1Struct.compatibleWith(expr2Struct)) {
			condFact_relop.struct = boolType;
		} else {
			condFact_relop.struct = Tab.noType;
		}
	}
	
	@Override
	public void visit(CondFactList_list condFactList_list) {
		Struct left = condFactList_list.getCondFactList().struct;
		Struct right = condFactList_list.getCondFact().struct;
		
		if(left.equals(boolType) && right.equals(boolType))
			condFactList_list.struct = boolType;
		else {
			report_error("And operacija ne bool vrednosti.", condFactList_list);
			condFactList_list.struct = Tab.noType;
		}
	}
	
	@Override
	public void visit(CondFactList_fact condFactList_fact) {
		condFactList_fact.struct = condFactList_fact.getCondFact().struct;
	}
	
	@Override
	public void visit(CondTerm condTerm) {
		condTerm.struct = condTerm.getCondFactList().struct;
	}
	
	@Override
	public void visit(CondTermList_list condTermList_list) {
		Struct left = condTermList_list.getCondTermList().struct;
		Struct right = condTermList_list.getCondTerm().struct;
		
		if(left.equals(boolType) && right.equals(boolType))
			condTermList_list.struct = boolType;
		else {
			report_error("OR operacija ne bool vrednosti.", condTermList_list);
			condTermList_list.struct = Tab.noType;
		}		
	}
	
	@Override
	public void visit(CondTermList_term condTermList_term) {
		condTermList_term.struct = condTermList_term.getCondTerm().struct;
	}
	
	@Override
	public void visit(Condition condition) {
		condition.struct = condition.getCondTermList().struct;
	}
	
	private boolean isDerived(Struct c, Struct p) {
		for (; c != null; c = c.getElemType())
	        if (c.equals(p))
	            return true;
	    return false;
	}
	
	//Designator Statements
	@Override
	public void visit(DesignatorStatement_assign designatorStatement_assign) {
		Obj desObj = designatorStatement_assign.getDesignator().obj;
		
		//if (desObj == null) return;
		
		int kind = desObj.getKind();
		Struct desStruct = desObj.getType(),
				exprStruct = designatorStatement_assign.getExpr().struct;
		
		if(kind != Obj.Var && kind != Obj.Elem && kind != Obj.Fld) {
			report_error("Dodela u neadekvatnu promenljivu: " + designatorStatement_assign.getDesignator().obj.getName(), designatorStatement_assign);
			return;
		}
			
		if(exprStruct.assignableTo(desStruct))
			return;
		
		if (desStruct.getKind() == Struct.Class 
				&& exprStruct.getKind() == Struct.Class
				&& isDerived(exprStruct, desStruct)) {
			report_info("Polimorfna dodela ", designatorStatement_assign);
			return;
		}
		
		if (desStruct.getKind() == Struct.Int && exprStruct.getKind() == Struct.Enum)
			return;
		
		report_error("Nemoguca dodela", designatorStatement_assign);
	}
	
	@Override
	public void visit(DesignatorStatement_inc designatorStatement_inc) {
		int kind = designatorStatement_inc.getDesignator().obj.getKind();
		Struct struct = designatorStatement_inc.getDesignator().obj.getType();
		if(kind != Obj.Var && kind != Obj.Elem && kind != Obj.Fld) 
			report_error("Inkrement neadekvatne promenljive: " + designatorStatement_inc.getDesignator().obj.getName(), designatorStatement_inc);
		else if(!struct.equals(Tab.intType))
			report_error("Inkrement ne int promenljive: " + designatorStatement_inc.getDesignator().obj.getName(), designatorStatement_inc);
	}
	
	@Override
	public void visit(DesignatorStatement_dec designatorStatement_dec) {
		int kind = designatorStatement_dec.getDesignator().obj.getKind();
		Struct struct = designatorStatement_dec.getDesignator().obj.getType();
		if(kind != Obj.Var && kind != Obj.Elem && kind != Obj.Fld) 
			report_error("Dekrement neadekvatne promenljive: " + designatorStatement_dec.getDesignator().obj.getName(), designatorStatement_dec);
		else if(!struct.equals(Tab.intType))
			report_error("Dekrement ne int promenljive: " + designatorStatement_dec.getDesignator().obj.getName(), designatorStatement_dec);
	}
	
	@Override
	public void visit(DesignatorStatement_meth designatorStatement_meth) {
		Obj obj = designatorStatement_meth.getMethodInvokeName().obj;
		if(obj.getKind() != Obj.Meth) {
			report_error("Ime " + obj.getName() + " nije naziv metode.", designatorStatement_meth);
		}
	}
	
	// Statement
	@Override
	public void visit(Statement_read statement_read) {
		int kind = statement_read.getDesignator().obj.getKind();
		Struct type = statement_read.getDesignator().obj.getType();
		if(kind != Obj.Var && kind != Obj.Elem && kind != Obj.Fld)
			report_error("Read operacija neadekvatne promenljive: " + statement_read.getDesignator().obj.getName(), statement_read);
		else if(!type.equals(Tab.intType) && !type.equals(Tab.charType) && !type.equals(boolType))
			report_error("Read operacija ne int/char/bool promenljive: " + statement_read.getDesignator().obj.getName(), statement_read);
	}
	
	@Override
	public void visit(Statement_print1 statement_print1) {
		Struct type = statement_print1.getExpr().struct;
		if (type.getKind() == Struct.Enum) {
			report_info("Implicitna konverzija izraza iz tipa enum u int.", statement_print1);
			type = statement_print1.getExpr().struct = Tab.intType;
		}
		if(!type.equals(Tab.intType) 
				&& !type.equals(Tab.charType) 
				&& !type.equals(boolType))
			report_error("Print operacija ne int/char/bool izraza", statement_print1);
	}
	
	@Override
	public void visit(Statement_print2 statement_print2) {
		Struct type = statement_print2.getExpr().struct;
		if(!type.equals(Tab.intType) && !type.equals(Tab.charType) && !type.equals(boolType))
			report_error("Print operacija ne int/char/bool izraza", statement_print2);
	}
	
	@Override
	public void visit(Statement_return1 statement_return1) {
		returnHappend = true;
		if (currentMethod.getType() != Tab.noType) {
			report_error("Metoda " + currentMethod.getName() + " ocekuje povratni tip, a ne void", statement_return1);
		}
	}
	
	@Override
	public void visit(Statement_return2 statement_return2) {
		returnHappend = true;
		Struct exprStruct = statement_return2.getExpr().struct;
		if (currentMethod.getType() == Tab.noType) {
			report_error("Metoda " + currentMethod.getName() + " ima povratni tip void i ne ocekuje izraz u return naredbi", statement_return2);
		} else if (exprStruct != currentMethod.getType()) {
			report_error("Naredba return nema odgovarajuci povratni tip.", statement_return2);
		}
	}
	
	@Override
	public void visit(Statement_if statement_if) {
		if (statement_if.getCondition().struct == Tab.noType) {
			report_error("Uslov IFa je neadekvatnog tipa.", statement_if);
		}
	}
	
	// For
	
	private int for_depth = 0;
	
	@Override
	public void visit(ForCondition_cond forCondition_cond) {
		this.for_depth++;
		if (forCondition_cond.getCondition().struct != boolType) {
			report_error("Ne bool tip u uslovu for petlje", forCondition_cond);
		}
	}
	
	@Override
	public void visit(ForCondition_e forCondition_e) {
		this.for_depth++;
	}
	
	@Override
	public void visit(Statement_for statement_for) {
		this.for_depth--;
	}
	
	@Override
	public void visit(Statement_continue statement_continue) {
		if (this.for_depth == 0) {
			report_error("Continue statement izvan for petlje.", statement_continue);
		}
	}
	
	@Override
	public void visit(Statement_break statement_break) {
		if (this.for_depth == 0 && this.switch_depth == 0) {
			report_error("Break statement izvan for/switch.", statement_break);
		}
	}
	
	int switch_depth = 0;
	Stack<ArrayList<Integer>> cases = new Stack<ArrayList<Integer>>();
	
	@Override
	public void visit(Statement_switch statement_switch) {
		this.switch_depth--;
		cases.pop();
	}
	
	@Override
	public void visit(SwitchExpr switchExpr) {
		this.switch_depth++;
		if (switchExpr.getExpr().struct != Tab.intType) {
			report_error("Tip u switch naredbi mora biti int", switchExpr);
		}
		cases.push(new ArrayList<Integer>());
	}
	
	@Override
	public void visit(CaseNum caseNum) {
		if (cases.peek().contains(caseNum.getN1())) {
			report_error("Visestruka definicija za case " + caseNum.getN1(), caseNum);
		} else {
			cases.peek().add(caseNum.getN1());
		}
	}
	
	private void initDefaultMeth() {
		Obj ordObj = Tab.find("ord");
		Tab.openScope();
		Obj p = Tab.insert(Obj.Var, "ord_param", Tab.charType);
		p.setFpPos(1);
		Tab.chainLocalSymbols(ordObj);
		Tab.closeScope();
		ordObj.setLevel(1);
		
		Obj chrObj = Tab.find("chr");
		Tab.openScope();
		p = Tab.insert(Obj.Var, "chr_param", Tab.intType);
		p.setFpPos(1);
		Tab.chainLocalSymbols(chrObj);
		Tab.closeScope();
		chrObj.setLevel(1);
	}

	
	
}
