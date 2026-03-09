package dev.outerstellar.starter

import dev.outerstellar.starter.persistence.MessageRepository
import dev.outerstellar.starter.web.WebPageFactory
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.template.ViewModel

fun main() {
    val config = AppConfig.fromEnvironment()
    val dataSource = createDataSource(config)
    val repository = dev.outerstellar.starter.persistence.JooqMessageRepository(org.jooq.impl.DSL.using(dataSource, org.jooq.SQLDialect.H2))
    
    val factory = WebPageFactory(repository)
    val req = Request(Method.GET, "/?theme=dracula")
    val page = factory.buildHomePage(req)
    
    println(page.shell.themeCss)
}
