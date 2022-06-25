package com.myorg;

import software.amazon.awscdk.App;

public final class JavademoApp {
    public static void main(final String[] args) {
        App app = new App();

        new JavademoStack(app, "JavademoStack");

        app.synth();
    }
}
