package top.wcpe.ioc.example.component

import top.wcpe.taboolib.ioc.annotation.Component

@Component
class ExampleTextComponent {

    fun line(label: String, value: Any): String {
        return "$label=$value"
    }
}
