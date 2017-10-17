# 利用编译时注解增强方法

### 想法
我之前打印方法的出参、入参以及耗时，都是在方法上加上运行时注解，然后通过aop切面去实现。觉得有些地方可以再优化一下，比如：

1. 在任何一个方法上加上注解，都要能打印出参、入参信息，而不局限于spring管理的类方法。
2. 不要每次调用方法都要经过各种反射，而是编译时就把增强代码注入到方法第一行。这样性能更好。
3. 可以自定义使用什么日志框架输出日志。

### 实验环境
- 开发工具：idea或eclipse
- 项目管理工具：maven3.3.9
- jdk版本：1.8.0_144

### 实现

#### 创建maven项目
使用idea创建一个普通的maven项目hello。并创建两个子模块hello-core和hello-test。整体项目结构如下图：

![method-logger](https://kangyonggan.com/upload/method-logger.png)

#### 父模块pom.xml
```
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.kangyonggan.demo</groupId>
    <artifactId>hello</artifactId>
    <packaging>pom</packaging>
    <version>1.0-SNAPSHOT</version>

    <modules>
        <module>hello-core</module>
        <module>hello-test</module>
    </modules>

    <properties>
        <!--Project-->
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <project.build.version>1.0-SNAPSHOT</project.build.version>

        <!--Plugins-->
        <plugin.compiler.version>3.5.1</plugin.compiler.version>
        <plugin.compiler.level>1.8</plugin.compiler.level>
    </properties>

    <build>
        <pluginManagement>
            <plugins>
                <!--compiler plugin -->
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>${plugin.compiler.version}</version>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

### hello-core模块pom.xml
```
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <artifactId>hello</artifactId>
        <groupId>com.kangyonggan.demo</groupId>
        <version>1.0-SNAPSHOT</version>
    </parent>
    <modelVersion>4.0.0</modelVersion>

    <artifactId>hello-core</artifactId>

    <build>
        <plugins>
            <!--compiler plugin -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <source>${plugin.compiler.level}</source>
                    <target>${plugin.compiler.level}</target>
                    <encoding>${project.build.sourceEncoding}</encoding>
                    <compilerArgument>-proc:none</compilerArgument>
                </configuration>
            </plugin>
        </plugins>
    </build>

    <dependencies>
        <dependency>
            <groupId>sun.jdk</groupId>
            <artifactId>tools</artifactId>
            <version>1.5.0</version>
            <scope>system</scope>
            <systemPath>${java.home}/../lib/tools.jar</systemPath>
        </dependency>
    </dependencies>
</project>
```

> 注意：编译插件一定要配置：`<compilerArgument>-proc:none</compilerArgument>`, 否则编译时报错：

```
Bad service configuration file, or exception thrown while constructing Processor object: javax.annotation.processing.Processor: Provider com.kangyonggan.demo.hello.core.MethodLoggerProcessor not found
```

> 注意：一定要依赖tools.jar，否则编译时报错：

```
com.sun.tools.javac.processing does not exist
```

### 编译时注解
`MethodLogger.java`

```
package com.kangyonggan.demo.hello.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author kangyonggan
 * @since 9/28/17
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface MethodLogger {

}
```

### 注解处理器
`MethodLoggerProcessor.java`

```
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
```

### 注册注解处理器
在`resources/META-INF/services`目录下创建文件`javax.annotation.processing.Processor`， 文件内容如下：

```
com.kangyonggan.demo.hello.core.MethodLoggerProcessor
```

### hello-test模块pom.xml
```
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <artifactId>hello</artifactId>
        <groupId>com.kangyonggan.demo</groupId>
        <version>1.0-SNAPSHOT</version>
    </parent>
    <modelVersion>4.0.0</modelVersion>

    <artifactId>hello-test</artifactId>

    <build>
        <plugins>
            <!--compiler plugin -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <source>${plugin.compiler.level}</source>
                    <target>${plugin.compiler.level}</target>
                    <encoding>${project.build.sourceEncoding}</encoding>
                </configuration>
            </plugin>
        </plugins>
    </build>

    <dependencies>
        <dependency>
            <groupId>com.kangyonggan.demo</groupId>
            <artifactId>hello-core</artifactId>
            <version>1.0-SNAPSHOT</version>
        </dependency>
    </dependencies>
</project>
```

### 测试
`MethodLoggerTest.java`

```
package com.kangyonggan.demo.hello.test;

import com.kangyonggan.demo.hello.core.MethodLogger;

/**
 * @author kangyonggan
 * @since 10/17/17
 */
public class MethodLoggerTest {

    @MethodLogger
    public void test() {
        System.out.println("test");
    }

    public static void main(String[] args) {
        new MethodLoggerTest().test();
    }

}
```

运行main方法输出如下：

```
Hello world!!!
test
```

### 反编译MethodLoggerTest.class
```
//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.kangyonggan.demo.hello.test;

public class MethodLoggerTest {
    public MethodLoggerTest() {
    }

    public void test() {
        System.out.println("Hello world!!!");
        System.out.println("test");
    }

    public static void main(String[] args) {
        (new MethodLoggerTest()).test();
    }
}
```

### 源代码
Hello World的源代码托管在github上：[https://github.com/kangyonggan/method-logger-hello.git](https://github.com/kangyonggan/method-logger-hello.git)

想法实现的代码：[https://github.com/kangyonggan/method-logger.git](https://github.com/kangyonggan/method-logger.git)








