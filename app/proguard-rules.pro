# kotlinx.serialization: сохраняем сгенерированные сериализаторы моделей.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class ru.olimphelper.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class ru.olimphelper.**$$serializer { *; }
-keepclassmembers class ru.olimphelper.** {
    *** Companion;
}
