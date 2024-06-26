package org.arquillian.cube.kubernetes.fabric8.impl.visitor;

import io.fabric8.kubernetes.api.builder.Visitor;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.arquillian.cube.kubernetes.api.Configuration;
import org.arquillian.cube.kubernetes.fabric8.impl.SecretKeys;
import org.arquillian.cube.kubernetes.fabric8.impl.utils.Secrets;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SecretsAndServiceAccountVisitor implements Visitor {

    @Inject
    Instance<KubernetesClient> client;
    @Inject
    Instance<Configuration> configuration;

    @Override
    public void visit(Object element) {
        String serviceAccount = null;
        Set<Secret> secrets = new LinkedHashSet<>();

        if (element instanceof PodBuilder) {
            PodBuilder builder = (PodBuilder) element;
            serviceAccount = builder.buildSpec().getServiceAccountName();
            secrets.addAll(generateSecrets(builder.buildMetadata()));
        } else if (element instanceof PodTemplateSpecBuilder) {
            PodTemplateSpecBuilder builder = (PodTemplateSpecBuilder) element;
            serviceAccount = builder.buildSpec().getServiceAccountName();
            secrets.addAll(generateSecrets(builder.buildMetadata()));
        }

    }



    private void createServiceAccount(String serviceAccount, Set<Secret> secrets) {

        KubernetesClient client = this.client.get();
        Configuration configuration = this.configuration.get();

        List<ObjectReference> refs = new ArrayList<>();
        for (Secret secret : secrets) {
            refs.add(
                    new ObjectReferenceBuilder()
                            .withNamespace(configuration.getNamespace())
                            .withName(secret.getMetadata().getName())
                            .build()
            );
        }


        if (client.serviceAccounts().inNamespace(configuration.getNamespace()).withName(serviceAccount).get() == null) {

            client.serviceAccounts().inNamespace(configuration.getNamespace()).create(new io.fabric8.kubernetes.api.model.ServiceAccountBuilder()
                    .withNewMetadata()
                    .withName(serviceAccount)
                    .endMetadata()
                    .withSecrets(refs)
            .build());
        } else {
            client.serviceAccounts().inNamespace(configuration.getNamespace()).withName(serviceAccount).edit( s -> new io.fabric8.kubernetes.api.model.ServiceAccountBuilder(s)
                    .withSecrets(refs)
                    .build());
        }
    }

    private Set<Secret> generateSecrets(ObjectMeta meta) {
        KubernetesClient client = this.client.get();
        Configuration configuration = this.configuration.get();

        Set<Secret> secrets = new HashSet<>();
        Map<String, String> annotations = meta.getAnnotations();
        if (annotations != null && !annotations.isEmpty()) {
            for (Map.Entry<String, String> entry : annotations.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (SecretKeys.isSecretKey(key)) {
                    SecretKeys keyType = SecretKeys.fromValue(key);
                    for (String name : Secrets.getNames(value)) {
                        Map<String, String> data = new HashMap<>();

                        Secret secret = null;
                        try {
                            secret = client.secrets().inNamespace(configuration.getNamespace()).withName(name).get();
                        } catch (Exception e) {
                            // ignore - probably doesn't exist
                        }

                        if (secret == null) {
                            for (String c : Secrets.getContents(value, name)) {
                                data.put(c, keyType.generate());
                            }

                            secret = client.secrets().inNamespace(configuration.getNamespace()).create(new io.fabric8.kubernetes.api.model.SecretBuilder()
                                    .withNewMetadata()
                                    .withName(name)
                                    .endMetadata()
                                    .withData(data)
                                    .build());
                            secrets.add(secret);
                        }
                    }
                }
            }
        }
        return secrets;
    }
}
