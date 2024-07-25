package soot.dotnet.members;

import java.lang.reflect.Modifier;
import java.util.Arrays;

import soot.Body;
import soot.Local;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.VoidType;
import soot.dotnet.proto.ProtoAssemblyAllTypes.ParameterDefinition;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;

/**
 * Parameters in .NET can be passed by reference. Since Jimple does not support this semantic, we generate Wrapper classes to
 * pass instead.
 * 
 * Note that .NET (as of right now, mid 2024) does not support co- or contravariants in parameters and return types. As such,
 * overriding virtual methods does not pose a problem when we substitute parameters with reference wrappers.
 * 
 * @author Marc Miltenberger
 */
public class ByReferenceWrapperGenerator {
  public static final String WRAPPER_CLASS_NAME = "ByReferenceWrappers.Wrapper";

  public synchronized static SootClass getWrapperClass(Type t) {
    Scene scene = Scene.v();
    String name = WRAPPER_CLASS_NAME;
    SootClass sc = scene.getSootClassUnsafe(name);
    if (sc != null) {
      return sc;
    }
    sc = scene.makeSootClass(name, Modifier.FINAL | Modifier.STATIC);
    sc.setApplicationClass();
    SootField r = scene.makeSootField("r", RefType.v("System.Object"));
    r.setModifiers(Modifier.PUBLIC);
    sc.addField(r);

    SootMethod ctor = scene.makeSootMethod("<init>", Arrays.asList(RefType.v("System.Object")), VoidType.v());
    Jimple j = Jimple.v();
    JimpleBody b = j.newBody(ctor);
    ctor.setActiveBody(b);
    sc.addMethod(ctor);
    b.insertIdentityStmts();
    b.getUnits().add(j.newAssignStmt(j.newInstanceFieldRef(b.getThisLocal(), r.makeRef()), b.getParameterLocal(0)));
    b.getUnits().add(j.newReturnVoidStmt());

    sc.addMethod(ctor);
    return sc;
  }

  public static boolean needsWrapper(ParameterDefinition parameter) {
    return parameter.getIsIn() || parameter.getIsOut() || parameter.getIsRef();
  }

  /**
   * Inserts calls to wrap objects
   * 
   * @param jb
   *          the body to insert wrapper calls
   * @param wrapperClass
   *          the class to use for wrapping
   * @param arg
   *          the argument to wrap
   * @return the wrapped argument
   */
  public static Value insertWrapperCall(Body jb, SootClass wrapperClass, Value arg) {
    SootMethod mCtor = wrapperClass.getMethodByName("<init>");
    Jimple j = Jimple.v();
    Local l = j.newLocal("wrap", wrapperClass.getType());
    jb.getLocals().add(l);
    jb.getUnits().add(j.newAssignStmt(l, j.newNewExpr(wrapperClass.getType())));
    jb.getUnits().add(j.newInvokeStmt(j.newSpecialInvokeExpr(l, mCtor.makeRef(), arg)));
    return l;
  }

  /**
   * Returns a call to unwrap objects
   * 
   * @param wrapperClass
   *          the class to use for wrapping
   * @param argToUnwrap
   *          the argument to unwrap
   * @param target
   *          the target that should contain the target afterwards
   * @return the unwrapper statement
   */
  public static Unit getUnwrapCall(SootClass wrapperClass, Value argToUnwrap, Value target) {
    SootField f = getWrapperField(wrapperClass);
    Jimple j = Jimple.v();
    return j.newAssignStmt(target, j.newInstanceFieldRef(argToUnwrap, f.makeRef()));
  }

  public static SootField getWrapperField(SootClass wrapperClass) {
    return wrapperClass.getFieldByName("r");
  }

  /**
   * Returns a call to update a wrapped object
   * 
   * @param wrapped
   *          the wrapped variable
   * @param unwrapped
   *          the unwrapped variable
   * @return the call
   */
  public static Unit getUpdateWrappedValueCall(Local wrapped, Local unwrapped) {
    RefType rt = (RefType) wrapped.getType();
    SootField f = rt.getSootClass().getFieldByName("r");
    Jimple j = Jimple.v();
    return j.newAssignStmt(j.newInstanceFieldRef(wrapped, f.makeRef()), unwrapped);

  }
}
