package org.carlspring.strongbox.providers.repository;


import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.carlspring.strongbox.data.criteria.Paginator;
import org.carlspring.strongbox.data.criteria.Predicate;
import org.carlspring.strongbox.domain.ArtifactEntry;
import org.carlspring.strongbox.domain.RemoteArtifactEntry;
import org.carlspring.strongbox.event.CommonEventListenerRegistry;
import org.carlspring.strongbox.io.ArtifactOutputStream;
import org.carlspring.strongbox.io.RepositoryInputStream;
import org.carlspring.strongbox.io.RepositoryOutputStream;
import org.carlspring.strongbox.providers.io.RepositoryPath;
import org.carlspring.strongbox.providers.repository.event.RemoteRepositorySearchEvent;
import org.carlspring.strongbox.providers.repository.proxied.LocalStorageProxyRepositoryArtifactResolver;
import org.carlspring.strongbox.providers.repository.proxied.ProxyRepositoryArtifactResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * @author carlspring
 */
@Component
public class ProxyRepositoryProvider
        extends AbstractRepositoryProvider
{

    private static final Logger logger = LoggerFactory.getLogger(ProxyRepositoryProvider.class);

    private static final String ALIAS = "proxy";

    @Inject
    @LocalStorageProxyRepositoryArtifactResolver.LocalStorageProxyRepositoryArtifactResolverQualifier
    private ProxyRepositoryArtifactResolver proxyRepositoryArtifactResolver;

    @Inject
    private HostedRepositoryProvider hostedRepositoryProvider;
    
    @Inject
    private CommonEventListenerRegistry commonEventListenerRegistry;

    @Override
    public String getAlias()
    {
        return ALIAS;
    }

    @Override
    protected RepositoryInputStream getInputStream(RepositoryPath path)
        throws IOException
    {
        return hostedRepositoryProvider.getInputStream(path);
    }



    @Override
    protected RepositoryPath fetchPath(RepositoryPath repositoryPath)
        throws IOException
    {
        RepositoryPath targetPath = hostedRepositoryProvider.fetchPath(repositoryPath);
        if (targetPath != null && Files.isDirectory(targetPath))
        {
            return targetPath;
        }
        return Optional.ofNullable(targetPath)
                       .orElse(resolvePathForceFetch(repositoryPath));
    }

    public RepositoryPath resolvePathForceFetch(RepositoryPath repositoryPath) throws IOException
    {
        try(InputStream is = proxyRepositoryArtifactResolver.getInputStream(repositoryPath))
        {
            IOUtils.closeQuietly(is);
            return repositoryPath;
        }
        catch (IOException e)
        {
            logger.error(String.format("Failed to resolve Path for proxied artifact [%s]", repositoryPath),
                         e);
            throw e;
        }
    }    
    
    @Override
    protected RepositoryOutputStream getOutputStream(RepositoryPath repositoryPath)
            throws IOException
    {
        ArtifactOutputStream aos = (ArtifactOutputStream) Files.newOutputStream(repositoryPath);
        
        return decorate(repositoryPath, aos);
    }
    
    @Override
    public List<Path> search(String storageId,
                             String repositoryId,
                             Predicate predicate,
                             Paginator paginator)
    {
        RemoteRepositorySearchEvent event = new RemoteRepositorySearchEvent(storageId, repositoryId, predicate,
                paginator);
        commonEventListenerRegistry.dispatchEvent(event);

        return hostedRepositoryProvider.search(storageId, repositoryId, predicate, paginator);
    }

    @Override
    public Long count(String storageId,
                      String repositoryId,
                      Predicate predicate)
    {
        RemoteRepositorySearchEvent event = new RemoteRepositorySearchEvent(storageId, repositoryId, predicate, null);
        commonEventListenerRegistry.dispatchEvent(event);

        return hostedRepositoryProvider.count(storageId, repositoryId, predicate);
    }

    protected ArtifactEntry provideArtirfactEntry(String storageId,
                                                  String repositoryId,
                                                  String path)
    {
        RemoteArtifactEntry artifactEntry = Optional.of(super.provideArtirfactEntry(storageId, repositoryId, path))
                                                    .map(e -> e.getObjectId() == null ? new RemoteArtifactEntry()
                                                            : (RemoteArtifactEntry) e)
                                                    .get();
        artifactEntry.setIsCached(Boolean.TRUE);

        return artifactEntry;
    }

}
