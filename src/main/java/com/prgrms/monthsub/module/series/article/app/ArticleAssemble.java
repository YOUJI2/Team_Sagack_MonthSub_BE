package com.prgrms.monthsub.module.series.article.app;

import com.prgrms.monthsub.common.s3.S3Client;
import com.prgrms.monthsub.common.s3.config.S3.Bucket;
import com.prgrms.monthsub.module.part.user.app.provider.UserProvider;
import com.prgrms.monthsub.module.part.user.domain.User;
import com.prgrms.monthsub.module.series.article.converter.ArticleConverter;
import com.prgrms.monthsub.module.series.article.domain.Article;
import com.prgrms.monthsub.module.series.article.dto.ArticleEdit;
import com.prgrms.monthsub.module.series.article.dto.ArticleOne;
import com.prgrms.monthsub.module.series.article.dto.ArticlePost;
import com.prgrms.monthsub.module.series.series.app.SeriesService;
import com.prgrms.monthsub.module.series.series.domain.Series;
import com.prgrms.monthsub.module.worker.explusion.domain.Expulsion.ExpulsionImageName;
import com.prgrms.monthsub.module.worker.explusion.domain.Expulsion.ExpulsionImageStatus;
import com.prgrms.monthsub.module.worker.explusion.domain.ExpulsionService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class ArticleAssemble {

  private final ArticleService articleService;
  private final SeriesService seriesService;
  private final ExpulsionService expulsionService;
  private final UserProvider userProvider;
  private final ArticleConverter articleConverter;
  private final S3Client s3Client;

  public ArticleAssemble(
    ArticleService articleService,
    SeriesService seriesService,
    ExpulsionService expulsionService,
    UserProvider userProvider,
    ArticleConverter articleConverter,
    S3Client s3Client
  ) {
    this.articleService = articleService;
    this.seriesService = seriesService;
    this.expulsionService = expulsionService;
    this.userProvider = userProvider;
    this.articleConverter = articleConverter;
    this.s3Client = s3Client;
  }

  @Transactional
  public ArticlePost.Response createArticle(
    MultipartFile thumbnail,
    ArticlePost.Request request
  ) {
    Series series = this.seriesService.getById(request.seriesId());

    Long articleCount = this.articleService.countBySeriesId(request.seriesId());

    Article article = this.articleConverter.ArticlePostToEntity(
      series,
      request,
      articleCount.intValue() + 1
    );
    this.articleService.save(article);

    String thumbnailKey = this.uploadThumbnailImage(
      thumbnail,
      request.seriesId(),
      article.getId()
    );

    article.changeThumbnailKey(thumbnailKey);

    return new ArticlePost.Response(article.getId());
  }

  @Transactional
  public ArticleEdit.TextChangeResponse editArticle(
    Long id,
    ArticleEdit.TextChangeRequest request
  ) {
    Article article = articleService.find(id);
    article.changeWriting(request.title(), request.contents());

    return new ArticleEdit.TextChangeResponse(article.getId());
  }

  public ArticleOne.Response getArticleOne(
    Long id,
    Long userId
  ) {
    Article article = articleService.find(id);
    Long articleCount = this.articleService.countBySeriesId(id);
    User user = userProvider.findById(id);

    return articleConverter.articleToArticleOneResponse(article, articleCount, user);
  }

  @Transactional
  public String changeThumbnail(
    MultipartFile thumbnail,
    Long seriesId,
    Long articleId,
    Long userId
  ) {
    Article article = articleService.find(articleId);

    String originalThumbnailKey = article.getThumbnailKey();

    String thumbnailKey = this.uploadThumbnailImage(
      thumbnail,
      seriesId,
      articleId
    );

    expulsionService.save(
      userId, originalThumbnailKey, ExpulsionImageStatus.CREATED,
      ExpulsionImageName.ARTICLE_THUMBNAIL
    );

    article.changeThumbnailKey(thumbnailKey);

    return this.articleConverter.toThumbnailEndpoint(thumbnailKey);
  }

  protected String uploadThumbnailImage(
    MultipartFile image,
    Long seriesId,
    Long articleId
  ) {
    String key = Series.class.getSimpleName()
      .toLowerCase()
      + "/" + seriesId.toString()
      + "/" + Article.class.getSimpleName()
      .toLowerCase()
      + "/" + articleId.toString()
      + "/thumbnail/"
      + UUID.randomUUID() +
      this.s3Client.getExtension(image);

    return this.s3Client.upload(Bucket.IMAGE, image, key, S3Client.imageExtensions);
  }

}
