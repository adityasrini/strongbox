package org.carlspring.strongbox.providers.repository;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

import org.apache.commons.io.output.CountingOutputStream;
import org.carlspring.strongbox.artifact.coordinates.ArtifactCoordinates;
import org.carlspring.strongbox.configuration.Configuration;
import org.carlspring.strongbox.configuration.ConfigurationManager;
import org.carlspring.strongbox.data.criteria.Expression.ExpOperator;
import org.carlspring.strongbox.data.criteria.Paginator;
import org.carlspring.strongbox.data.criteria.Predicate;
import org.carlspring.strongbox.data.criteria.Selector;
import org.carlspring.strongbox.domain.ArtifactEntry;
import org.carlspring.strongbox.io.ArtifactOutputStream;
import org.carlspring.strongbox.io.RepositoryInputStream;
import org.carlspring.strongbox.io.RepositoryOutputStream;
import org.carlspring.strongbox.io.RepositoryStreamCallback;
import org.carlspring.strongbox.io.RepositoryStreamContext;
import org.carlspring.strongbox.io.StreamUtils;
import org.carlspring.strongbox.providers.datastore.StorageProviderRegistry;
import org.carlspring.strongbox.providers.io.RepositoryFiles;
import org.carlspring.strongbox.providers.io.RepositoryPath;
import org.carlspring.strongbox.providers.layout.LayoutProviderRegistry;
import org.carlspring.strongbox.services.ArtifactEntryService;
import org.carlspring.strongbox.services.ArtifactTagService;
import org.carlspring.strongbox.storage.repository.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * @author carlspring
 */
@Transactional
public abstract class AbstractRepositoryProvider implements RepositoryProvider, RepositoryStreamCallback
{

    private static final Logger logger = LoggerFactory.getLogger(AbstractRepositoryProvider.class);
    @Inject
    protected RepositoryProviderRegistry repositoryProviderRegistry;

    @Inject
    protected LayoutProviderRegistry layoutProviderRegistry;

    @Inject
    protected StorageProviderRegistry storageProviderRegistry;

    @Inject
    protected ConfigurationManager configurationManager;

    @Inject
    protected ArtifactEntryService artifactEntryService;
    
    @Inject
    protected ArtifactTagService artifactTagService;

    public RepositoryProviderRegistry getRepositoryProviderRegistry()
    {
        return repositoryProviderRegistry;
    }

    public void setRepositoryProviderRegistry(RepositoryProviderRegistry repositoryProviderRegistry)
    {
        this.repositoryProviderRegistry = repositoryProviderRegistry;
    }

    public LayoutProviderRegistry getLayoutProviderRegistry()
    {
        return layoutProviderRegistry;
    }

    public void setLayoutProviderRegistry(LayoutProviderRegistry layoutProviderRegistry)
    {
        this.layoutProviderRegistry = layoutProviderRegistry;
    }

    public StorageProviderRegistry getStorageProviderRegistry()
    {
        return storageProviderRegistry;
    }

    public void setStorageProviderRegistry(StorageProviderRegistry storageProviderRegistry)
    {
        this.storageProviderRegistry = storageProviderRegistry;
    }

    public ConfigurationManager getConfigurationManager()
    {
        return configurationManager;
    }

    public void setConfigurationManager(ConfigurationManager configurationManager)
    {
        this.configurationManager = configurationManager;
    }

    public Configuration getConfiguration()
    {
        return configurationManager.getConfiguration();
    }
    
    @Override
    public RepositoryInputStream getInputStream(Path path) throws IOException
    {
        if (path == null)
        {
            return null;
        }
        Assert.isInstanceOf(RepositoryPath.class, path);
        return getInputStream((RepositoryPath) path);
    }
    
    protected abstract RepositoryInputStream getInputStream(RepositoryPath path) throws IOException;

    protected RepositoryInputStream decorate(String storageId,
                                             String repositoryId,
                                             String path,
                                             InputStream is)
    {
        if (is == null || is instanceof RepositoryInputStream)
        {
            return (RepositoryInputStream) is;
        }

        Repository repository = configurationManager.getRepository(storageId, repositoryId);

        return RepositoryInputStream.of(repository, path, is).with(this);
    }

    @Override
    public RepositoryOutputStream getOutputStream(Path path)
        throws IOException,
        NoSuchAlgorithmException
    {
        Assert.isInstanceOf(RepositoryPath.class, path);
        return getOutputStream((RepositoryPath) path);
    }
    
    protected abstract RepositoryOutputStream getOutputStream(RepositoryPath repositoryPath)
        throws IOException;

    protected final RepositoryOutputStream decorate(RepositoryPath repositoryPath,
                                                    OutputStream os) throws IOException
    {
        if (os == null || os instanceof RepositoryOutputStream)
        {
            return (RepositoryOutputStream) os;
        }

        String path = RepositoryFiles.stringValue(repositoryPath);
        return RepositoryOutputStream.of(repositoryPath.getRepository(), path, os).with(this);
    }

    @Override
    public void onBeforeWrite(RepositoryStreamContext ctx)
    {
        logger.debug(String.format("Writing [%s]", ctx.getPath()));
        
        Repository repository = ctx.getRepository();
        String storageId = repository.getStorage().getId();
        String repositoryId = repository.getId();

        ArtifactEntry artifactEntry = provideArtirfactEntry(storageId, repositoryId,
                                                            ctx.getPath());

        artifactEntry.setStorageId(storageId);
        artifactEntry.setRepositoryId(repositoryId);
        artifactEntry.setArtifactPath(ctx.getPath());

        ArtifactOutputStream aos = StreamUtils.findSource(ArtifactOutputStream.class, (OutputStream) ctx);
        ArtifactCoordinates coordinates = aos.getCoordinates();
        artifactEntry.setArtifactCoordinates(coordinates);

        Date now = new Date();
        artifactEntry.setLastUpdated(now);
        artifactEntry.setLastUsed(now);

        artifactEntryService.save(artifactEntry, true);
    }

    @Override
    public void onAfterClose(RepositoryStreamContext ctx)
    {
        logger.debug(String.format("Closing [%s]", ctx.getPath()));

        Repository repository = ctx.getRepository();
        String storageId = repository.getStorage().getId();
        String repositoryId = repository.getId();

        ArtifactEntry artifactEntry = provideArtirfactEntry(storageId, repositoryId,
                                                            ctx.getPath());
        Assert.notNull(artifactEntry.getUuid(),
                       String.format("Invalid [%s] for [%s]", ArtifactEntry.class.getSimpleName(),
                                     ctx.getPath()));

        CountingOutputStream cos = StreamUtils.findSource(CountingOutputStream.class, (OutputStream) ctx);
        artifactEntry.setSizeInBytes(cos.getByteCount());

        artifactEntryService.save(artifactEntry);
    }

    @Override
    public void onBeforeRead(RepositoryStreamContext ctx)
    {
        logger.debug(String.format("Reading [%s]", ctx.getPath()));

        Repository repository = ctx.getRepository();
        String storageId = repository.getStorage().getId();
        String repositoryId = repository.getId();

        ArtifactEntry artifactEntry = provideArtirfactEntry(storageId, repositoryId,
                                                            ctx.getPath());

        artifactEntry.setLastUsed(new Date());
        artifactEntry.setDownloadCount(artifactEntry.getDownloadCount() + 1);

        artifactEntryService.save(artifactEntry);
    }

    protected ArtifactEntry provideArtirfactEntry(String storageId,
                                                  String repositoryId,
                                                  String path)
    {
        ArtifactEntry artifactEntry = artifactEntryService.findOneArtifact(storageId,
                                                                           repositoryId,
                                                                           path)
                                                          .map(e -> artifactEntryService.lockOne(e.getObjectId()))
                                                          .orElse(new ArtifactEntry());

        return artifactEntry;
    }
    
    @Override
    public Path fetchPath(Path repositoryPath)
        throws IOException
    {
        return fetchPath((RepositoryPath)repositoryPath);
    }

    protected abstract Path fetchPath(RepositoryPath repositoryPath) throws IOException;
    
    @Override
    public List<Path> search(RepositorySearchRequest searchRequest,
                             RepositoryPageRequest pageRequest)
    {
        Paginator paginator = new Paginator();
        paginator.setLimit(pageRequest.getLimit());
        paginator.setSkip(pageRequest.getSkip());

        Predicate p = createPredicate(searchRequest);        
        
        return search(searchRequest.getStorageId(), searchRequest.getRepositoryId(), p, paginator);
    }    
    
    @Override
    public Long count(RepositorySearchRequest searchRequest)
    {
        Predicate p = createPredicate(searchRequest);
        return count(searchRequest.getStorageId(), searchRequest.getRepositoryId(), p);
    }
    
    protected Predicate createPredicate(RepositorySearchRequest searchRequest)
    {
        Predicate p = Predicate.empty();

        searchRequest.getCoordinates()
                     .entrySet()
                     .forEach(e -> p.and(createCoordinatePredicate(e.getKey(), e.getValue(),
                                                                   searchRequest.isStrict())));

        searchRequest.getTagSet()
                     .forEach(t -> p.and(Predicate.of(ExpOperator.CONTAINS.of("tagSet.name", t.getName()))));

        return p;
    }
    
    protected Predicate createPredicate(String storageId,
                                        String repositoryId,
                                        Predicate predicate)
    {
        Predicate result = Predicate.of(ExpOperator.EQ.of("storageId",
                                                          storageId))
                                    .and(Predicate.of(ExpOperator.EQ.of("repositoryId",
                                                                        repositoryId)));
        if (predicate.isEmpty())
        {
            return result;
        }
        return result.and(predicate);
    }

    private Predicate createCoordinatePredicate(String key,
                                                String value,
                                                boolean strict)
    {
        if (!strict)
        {
            return Predicate.of(ExpOperator.LIKE.of(String.format("artifactCoordinates.coordinates.%s",
                                                                  key),
                                                    "%"+ value + "%"));
        }
        return Predicate.of(ExpOperator.EQ.of(String.format("artifactCoordinates.coordinates.%s",
                                                            key),
                                              value));
    }
    
    protected Selector<ArtifactEntry> createSelector(String storageId,
                                                     String repositoryId,
                                                     Predicate p)
    {
        Selector<ArtifactEntry> selector = new Selector<>(ArtifactEntry.class);
        selector.where(createPredicate(storageId, repositoryId, p));
        
        return selector;
    }
    
}
