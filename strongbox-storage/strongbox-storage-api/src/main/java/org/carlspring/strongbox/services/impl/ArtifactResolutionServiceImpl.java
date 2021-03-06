package org.carlspring.strongbox.services.impl;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.security.NoSuchAlgorithmException;

import javax.inject.Inject;

import org.carlspring.strongbox.configuration.ConfigurationManager;
import org.carlspring.strongbox.io.RepositoryInputStream;
import org.carlspring.strongbox.io.RepositoryOutputStream;
import org.carlspring.strongbox.providers.io.RepositoryFiles;
import org.carlspring.strongbox.providers.io.RepositoryPath;
import org.carlspring.strongbox.providers.io.RepositoryPathResolver;
import org.carlspring.strongbox.providers.repository.RepositoryProvider;
import org.carlspring.strongbox.providers.repository.RepositoryProviderRegistry;
import org.carlspring.strongbox.services.ArtifactResolutionService;
import org.carlspring.strongbox.storage.ArtifactResolutionException;
import org.carlspring.strongbox.storage.ArtifactStorageException;
import org.carlspring.strongbox.storage.Storage;
import org.carlspring.strongbox.storage.repository.Repository;
import org.carlspring.strongbox.storage.validation.resource.ArtifactOperationsValidator;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * @author mtodorov
 */
@Component
public class ArtifactResolutionServiceImpl
        implements ArtifactResolutionService
{

    @Inject
    private ConfigurationManager configurationManager;

    @Inject
    private ArtifactOperationsValidator artifactOperationsValidator;

    @Inject
    private RepositoryProviderRegistry repositoryProviderRegistry;
    
    @Inject
    private RepositoryPathResolver repositoryPathResolver;

    @Override
    public RepositoryInputStream getInputStream(RepositoryPath path)
        throws IOException
    {
        Repository repository = path.getFileSystem().getRepository();
        artifactOperationsValidator.validate(path);
        
        RepositoryProvider repositoryProvider = repositoryProviderRegistry.getProvider(repository.getType());
        RepositoryInputStream is = repositoryProvider.getInputStream(path);
        if (is == null)
        {
            throw new ArtifactResolutionException(String.format("Artifact [%s] not found.", path));
        }

        return is;
    }

    @Override
    public RepositoryOutputStream getOutputStream(RepositoryPath repositoryPath)
        throws IOException,
        NoSuchAlgorithmException
    {
        artifactOperationsValidator.validate(repositoryPath);

        Repository repository = repositoryPath.getRepository();
        RepositoryProvider repositoryProvider = repositoryProviderRegistry.getProvider(repository.getType());

        RepositoryOutputStream os = repositoryProvider.getOutputStream(repositoryPath);
        if (os == null)
        {
            throw new ArtifactStorageException("Artifact " + repositoryPath + " cannot be stored.");
        }

        return os;
    }

    public Storage getStorage(String storageId)
    {
        return configurationManager.getConfiguration().getStorage(storageId);
    }

    @Override
    public URL resolveResource(RepositoryPath repositoryPath)
            throws IOException
    {
        URI baseUri = configurationManager.getBaseUri();

        Repository repository = repositoryPath.getRepository();
        Storage storage = repository.getStorage();
        URI artifactResource = RepositoryFiles.resolveResource(repositoryPath);

        return UriComponentsBuilder.fromUri(baseUri)
                                   .pathSegment("storages", storage.getId(), repository.getId(), "/")
                                   .build()
                                   .toUri()
                                   .resolve(artifactResource)
                                   .toURL();
    }
    
    @Override
    public RepositoryPath resolvePath(String storageId,
                                      String repositoryId,
                                      String artifactPath) 
           throws IOException
    {        
        RepositoryPath repositoryPath = repositoryPathResolver.resolve(storageId, repositoryId, artifactPath);
        
        Repository repository = repositoryPath.getRepository();
        RepositoryProvider repositoryProvider = repositoryProviderRegistry.getProvider(repository.getType());
        
        return (RepositoryPath)repositoryProvider.fetchPath(repositoryPath);
    }
    
}
