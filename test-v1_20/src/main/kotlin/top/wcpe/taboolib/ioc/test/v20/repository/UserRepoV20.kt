package top.wcpe.taboolib.ioc.test.v20.repository

import top.wcpe.taboolib.ioc.annotation.Repository

@Repository
class UserRepoV20 {

    private val store = mutableMapOf<String, String>()

    fun save(id: String, name: String) {
        store[id] = name
    }

    fun findAll(): Map<String, String> = store.toMap()

    fun findById(id: String): String? = store[id]
}
