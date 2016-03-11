package Semant;
import Translate.Exp;
import Types.Type;

public class Semant {
  Env env;
  public Semant(ErrorMsg.ErrorMsg err) {
    this(new Env(err));
  }
  Semant(Env e) {
    env = e;
  }

  public void transProg(Absyn.Exp exp) {
    transExp(exp);
  }

  private void error(int pos, String msg) {
    env.errorMsg.error(pos, msg);
  }

  static final Types.VOID   VOID   = new Types.VOID();
  static final Types.INT    INT    = new Types.INT();
  static final Types.STRING STRING = new Types.STRING();
  static final Types.NIL    NIL    = new Types.NIL();

  private Exp checkInt(ExpTy et, int pos) {
    if (!INT.coerceTo(et.ty))
      error(pos, "integer required");
    return et.exp;
  }

  private Exp checkComparable(ExpTy et, int pos) {
    if(!(INT.coerceTo(et.ty) || STRING.coerceTo(et.ty)))
      error(pos, "integer or string required");
    return et.exp;
  }

  private Exp checkEquable(ExpTy et, int pos) {
    //need to avoid making a record/array
    Type type = et.ty.actual();
    if(!(type instanceof Types.INT || type instanceof Types.STRING || type instanceof Types.RECORD || type instanceof Types.ARRAY))
      error(pos, "integer, string, record, or array required");
    return et.exp;
  }

  ExpTy transExp(Absyn.Exp e) {
    ExpTy result;

    if (e == null)
      return new ExpTy(null, VOID);
    else if (e instanceof Absyn.OpExp)
      result = transExp((Absyn.OpExp)e);
    else if (e instanceof Absyn.LetExp)
      result = transExp((Absyn.LetExp)e);
    else if (e instanceof Absyn.IfExp)
      result = transExp((Absyn.IfExp)e);
    else if (e instanceof Absyn.IntExp)
      result = new ExpTy(null, INT);
    else if (e instanceof Absyn.StringExp)
      result = new ExpTy(null, STRING);
    else if (e instanceof Absyn.NilExp)
      result = new ExpTy(null, NIL);
    else if (e instanceof Absyn.ArrayExp)
      result = transExp((Absyn.ArrayExp)e);
    else if (e instanceof Absyn.VarExp)
      result = transExp((Absyn.VarExp)e);
    else throw new Error("Failed for "+e.getClass().getName());
    e.type = result.ty;
    return result;
  }

  ExpTy transExp(Absyn.ArrayExp e) {
    //extract and typecheck the array information
    Types.NAME type = (Types.NAME)env.tenv.get(e.typ);
    ExpTy size = transExp(e.size);
    ExpTy init = transExp(e.init);

    return new ExpTy(null, type);
  }

  ExpTy transExp(Absyn.VarExp e) {
    //Likely will have to split into overloaded methods?
    Absyn.Var var = e.var;
    Types.NAME type;
    Entry entry;
    //REMEMBER: Entries are put into the venv
    if(var instanceof Absyn.SimpleVar) {
        entry = (Entry)env.venv.get(((Absyn.SimpleVar)var).name);
        return new ExpTy(null, ((VarEntry)entry).ty);
    } else { 
        throw new Error("varExp "+var.getClass().getName());
    }
  }
  
  ExpTy transExp(Absyn.IfExp e) {
    ExpTy test = transExp(e.test);
    //check for int
    checkInt(test, e.test.pos);
    //the test is an integer, get type of then clause
    ExpTy then = transExp(e.thenclause);
    //check if we have an else clause
    if(e.elseclause != null) {
      ExpTy els = transExp(e.elseclause);
      //need to be same type
      if(!(els.ty.coerceTo(then.ty)))
        error(e.thenclause.pos, "then/else clause must be same type");
    }

    //TODO: Might need to determine type based on whether or not else clause is present
    return then;
  }

  ExpTy transExp(Absyn.OpExp e) {
    ExpTy left = transExp(e.left);
    ExpTy right = transExp(e.right);

    switch (e.oper) {
    case Absyn.OpExp.PLUS:
      checkInt(left, e.left.pos);
      checkInt(right, e.right.pos);
      return new ExpTy(null, INT);
    case Absyn.OpExp.MINUS:
      checkInt(left, e.left.pos);
      checkInt(right, e.right.pos);
      return new ExpTy(null, INT);
    case Absyn.OpExp.MUL:
      checkInt(left, e.left.pos);
      checkInt(right, e.right.pos);
      return new ExpTy(null, INT);
    case Absyn.OpExp.DIV:
      checkInt(left, e.left.pos);
      checkInt(right, e.right.pos);
      return new ExpTy(null, INT);
    case Absyn.OpExp.GT:
    case Absyn.OpExp.LT:
    case Absyn.OpExp.GE:
    case Absyn.OpExp.LE:
      checkComparable(left, e.left.pos);
      checkComparable(right, e.right.pos);
      return new ExpTy(null, INT);
    case Absyn.OpExp.EQ:
    case Absyn.OpExp.NE:
      checkEquable(left, e.left.pos);
      checkEquable(left, e.left.pos);
      return new ExpTy(null, INT);
    default:
      throw new Error("unknown operator");
    }
  }

  ExpTy transExp(Absyn.LetExp e) {
    env.venv.beginScope();
    env.tenv.beginScope();
    for (Absyn.DecList d = e.decs; d != null; d = d.tail) {
      transDec(d.head);
    }
    ExpTy body = transExp(e.body);
    env.venv.endScope();
    env.tenv.endScope();
    return new ExpTy(null, body.ty);
  }

  Exp transDec(Absyn.Dec d) {
    if (d instanceof Absyn.VarDec)
      return transDec((Absyn.VarDec)d);
    if (d instanceof Absyn.TypeDec)
      return transDec((Absyn.TypeDec)d);
    throw new Error("Failed for "+d.getClass().getName());
  }

  Exp transDec(Absyn.TypeDec d) {
    System.out.println(d.ty.getClass().getName());
    //Make a NAME for this type
    Types.NAME name = new Types.NAME(d.name);
    //Bind the name to the type that is declared
    name.bind(transTy(d.ty));
    d.entry = name;
    //TODO: ERROR
    env.tenv.put(d.name, name);
    //TOOD: go down the TypeDec list....
    return null;
  }

  Types.Type transTy(Absyn.Ty t) {
    //using instanceof, translate param Absyn.Ty into appropriate Types.Type
    if(t instanceof Absyn.RecordTy)
      return transTy((Absyn.RecordTy) t);
    if(t instanceof Absyn.ArrayTy)
      return transTy((Absyn.ArrayTy) t);
    throw new Error("Failed for "+t.getClass().getName());
  }

  Types.ARRAY transTy(Absyn.ArrayTy t) {
    //look up the type of the array in the type environment
    Types.Type type = (Types.Type)env.tenv.get(t.typ);
    //if the lookup fails, something bad happened
    if(type == null) {
      error(t.pos, "array type not known");
      return null;
    }
    return new Types.ARRAY(type);
  }

  Types.RECORD transTy(Absyn.RecordTy t) {
    throw new Error("Semant.transTy for records unimplemented");
  }

  Exp transDec(Absyn.VarDec d) {
    // NOTE: THIS IMPLEMENTATION IS INCOMPLETE
    // It is here to show you the general form of the transDec methods
    ExpTy init = transExp(d.init);
    Type type;
    if (d.typ == null) {
      //Unknown type? i.e. recursive type?
      type = init.ty;
    } else {
      type = init.ty;
      /**
      type = VOID;
      throw new Error("unimplemented declaration: "+d.typ.getClass().getName());
      */
    }
    d.entry = new VarEntry(type);
    env.venv.put(d.name, d.entry);
    return null;
  }
}
