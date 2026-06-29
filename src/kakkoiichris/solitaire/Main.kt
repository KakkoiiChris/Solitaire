package kakkoiichris.solitaire

import kakkoiichris.hypergame.view.Window
import kakkoiichris.solitaire.Solitaire.resources

fun main() {
    val icon = resources.getFolder("img").getSprite("icon")

    val display = Window(1200, 900, title = "Solitaire", icon = icon)

    display.open(Solitaire)
}

class X(val a:Int)