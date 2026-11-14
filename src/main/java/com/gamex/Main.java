package com.gamex;

import godot.annotation.Script;
import godot.api.Node;
import godot.global.GD;

/** Root node of the main scene. */
@Script
public class Main extends Node {

    @Override
    public void _ready() {
        GD.print("[gamex] Main._ready() running on the JVM, Java " + Runtime.version());
    }
}
