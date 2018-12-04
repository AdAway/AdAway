package io.sentry.event;

public class BreadcrumbBuilder {

    public BreadcrumbBuilder setMessage(String message) {
        // Stub
        return this;
    }

    public Breadcrumb build() {
        return new Breadcrumb();
    }
}
