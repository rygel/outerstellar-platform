#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.extension

import org.http4k.template.ViewModel

data class IndexPage(
    val platformVersion: String,
) : ViewModel {
    override fun template() = "IndexPage"
}
