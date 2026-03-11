package top.wcpe.ioc.example.repository

import top.wcpe.taboolib.ioc.annotation.Repository

@Repository
class ExampleUserRepository {
    fun loadStatus(): String = "ioc-ready"
}
