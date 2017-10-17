package com.kangyonggan.demo.hello.core;

import com.sun.source.util.Trees;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import java.util.Set;


/**
 * 注解处理器
 *
 * @author kangyonggan
 * @since 9/28/17
 */
@SupportedAnnotationTypes("com.kangyonggan.demo.hello.core.MethodLogger")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class MethodLoggerProcessor extends AbstractProcessor {

    private Trees trees;
    private TreeMaker treeMaker;
    private Name.Table names;

    /**
     * 初始化，获取编译环境
     *
     * @param env
     */
    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);

        trees = Trees.instance(env);
        Context context = ((JavacProcessingEnvironment) env).getContext();
        treeMaker = TreeMaker.instance(context);
        names = Names.instance(context).table;
    }

    /**
     * 处理注解
     *
     * @param annotations
     * @param env
     * @return
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
        // 处理有@MethodLogger注解的元素
        for (Element element : env.getElementsAnnotatedWith(MethodLogger.class)) {
            // 只处理作用在方法上的注解
            if (element.getKind() == ElementKind.METHOD) {
                JCTree tree = (JCTree) trees.getTree(element);
                tree.accept(new TreeTranslator() {
                    /**
                     * 方法的代码块，在代码块的第一行添加代码：System.out.println("Hello World!!!");
                     *
                     * @param tree
                     */
                    @Override
                    public void visitBlock(JCTree.JCBlock tree) {
                        ListBuffer<JCTree.JCStatement> statements = new ListBuffer();

                        // 创建代码: System.out.println("Hello World!!!");
                        JCTree.JCFieldAccess fieldAccess = treeMaker.Select(treeMaker.Select(treeMaker.Ident(names.fromString("System")), names.fromString("out")), names.fromString("println"));
                        JCTree.JCExpression argsExpr = treeMaker.Literal("Hello world!!!");
                        JCTree.JCMethodInvocation methodInvocation = treeMaker.Apply(List.nil(), fieldAccess, List.of(argsExpr));
                        JCTree.JCExpressionStatement code = treeMaker.Exec(methodInvocation);

                        // 把代码加到方法体之前
                        statements.append(code);

                        // 把原来的方法体写回去
                        for (int i = 0; i < tree.getStatements().size(); i++) {
                            statements.append(tree.getStatements().get(i));
                        }

                        result = treeMaker.Block(0, statements.toList());
                    }
                });

            }
        }
        return true;
    }

}
