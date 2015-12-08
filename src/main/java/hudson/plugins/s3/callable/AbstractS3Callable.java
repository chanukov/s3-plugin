package hudson.plugins.s3.callable;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.transfer.TransferManager;
import hudson.plugins.s3.ClientHelper;
import hudson.util.Secret;

import java.io.Serializable;

public class AbstractS3Callable implements Serializable
{
    private static final long serialVersionUID = 1L;

    private final String accessKey;
    private final Secret secretKey;
    private final boolean useRole;

    private transient static volatile AmazonS3Client client;
    private transient static volatile TransferManager transferManager;
    private transient static String oldClient;

    public AbstractS3Callable(String accessKey, Secret secretKey, boolean useRole)
    {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.useRole = useRole;
    }

    protected synchronized AmazonS3Client getClient()
    {
        String newClient = getHash(accessKey, secretKey, useRole);

        if (client == null || !newClient.equals(oldClient)) {
            client = ClientHelper.createClient(accessKey, secretKey, useRole);
            oldClient = newClient;
        }

        return client;
    }

    private String getHash(String access, Secret secret, boolean useRole) {
        return access + secret.getPlainText() + Boolean.toString(useRole);
    }

    protected synchronized TransferManager getTransferManager()
    {
        if (transferManager == null) {
            transferManager = new TransferManager(getClient());
        }
        else {
            AmazonS3 oldClient = transferManager.getAmazonS3Client();
            AmazonS3 newClient = getClient();
            if (!newClient.equals(oldClient)) {
                while (transferManager != null) {
                    try {
                        transferManager.wait();
                        transferManager.shutdownNow();
                        transferManager = null;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            transferManager = new TransferManager(getClient());
            }
        }

        return transferManager;
    }

}
