package org.arquillian.cube.kubernetes.impl.visitor;

import io.fabric8.kubernetes.api.builder.Visitor;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.arquillian.cube.impl.util.Strings;
import org.arquillian.cube.kubernetes.api.Configuration;
import org.arquillian.cube.kubernetes.api.Logger;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.Inject;

public class ServiceAccountVisitor implements Visitor {

    @Inject
    protected Instance<Logger> logger;
    @Inject
    Instance<KubernetesClient> client;
    @Inject
    Instance<Configuration> configuration;

    @Override
    public void visit(Object element) {
        if (element instanceof PodSpecBuilder) {
            PodSpecBuilder builder = (PodSpecBuilder) element;
            String serviceAccount = builder.getServiceAccountName();
            if (Strings.isNotNullOrEmpty(serviceAccount) && !serviceAccountExists(serviceAccount)) {
                try {
                    createServiceAccount(serviceAccount);
                } catch (Throwable t) {
                    logger.get().warn("Failed to create ServiceAccount with name:[" + serviceAccount + "].");
                }
            }
        }
    }

    private boolean serviceAccountExists(String serviceAccount) {
        KubernetesClient client = this.client.get();
        Configuration configuration = this.configuration.get();
        return client.serviceAccounts().inNamespace(configuration.getNamespace()).withName(serviceAccount).get() != null;
    }

    private void createServiceAccount(String serviceAccount) {
        KubernetesClient client = this.client.get();
        Configuration configuration = this.configuration.get();
        client.serviceAccounts().inNamespace(configuration.getNamespace()).create(new ServiceAccountBuilder()
            .withNewMetadata()
            .withName(serviceAccount)
            .endMetadata().build());
    }
}
