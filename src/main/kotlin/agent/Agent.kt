package agent

import jdk.internal.org.objectweb.asm.ClassReader
import jdk.internal.org.objectweb.asm.ClassVisitor
import jdk.internal.org.objectweb.asm.ClassWriter
import jdk.internal.org.objectweb.asm.Opcodes
import jdk.internal.org.objectweb.asm.tree.*
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain

class Agent {
    companion object {
        @JvmStatic
        fun premain(agentArgs: String?, inst: Instrumentation) {
            println("Agent started.")
            inst.addTransformer(TestInvocationTransformer())
        }
    }
}

class TestInvocationTransformer : ClassFileTransformer {
    override fun transform(loader: ClassLoader?, className: String?, classBeingRedefined: Class<*>?,
                           protectionDomain: ProtectionDomain?, classfileBuffer: ByteArray?): ByteArray? {
        val cw = ClassWriter(0)
        val ca = TransformationAdapter(cw)
        val cr = ClassReader(classfileBuffer)
        cr.accept(ca, 0)
        return cw.toByteArray()
    }
}

class TransformationAdapter(val visitor: ClassVisitor) : ClassNode(Opcodes.ASM5) {
    /*  Possibly we could utilize visitor pattern more
        but for me this variant looks tidier as it is only test example
     */
    override fun visitEnd() {
        for (methodNode in methods) {
            methodNode.instructions.iterator().asSequence()
                    .filter {
                        it.opcode == Opcodes.INVOKESTATIC && it is MethodInsnNode &&
                                it.owner == "example/CoroutineExampleKt" && it.name == "test" &&
                                it.desc == "(Lkotlin/coroutines/experimental/Continuation;)Ljava/lang/Object;"
                    }
                    .forEach {
                        val printInstructions = InsnList()
                        printInstructions.add(
                                FieldInsnNode(Opcodes.GETSTATIC,
                                        "java/lang/System",
                                        "out",
                                        "Ljava/io/PrintStream;")
                        )
                        printInstructions.add(
                                LdcInsnNode("Test detected")
                        )
                        printInstructions.add(
                                MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                                        "java/io/PrintStream",
                                        "println",
                                        "(Ljava/lang/Object;)V", false)
                        )
                        methodNode.instructions.insertBefore(it, printInstructions)

                        /* Be sure that there's enough stack slots
                           as we have two additional objects on stack
                         */
                        methodNode.maxStack += 2
                    }
        }
        accept(visitor)
    }
}

