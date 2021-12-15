package com.prgrms.monthsub.module.payment.app;

import com.prgrms.monthsub.module.part.user.app.provider.UserProvider;
import com.prgrms.monthsub.module.part.user.domain.User;
import com.prgrms.monthsub.module.payment.converter.PaymentConverter;
import com.prgrms.monthsub.module.payment.domain.exception.PaymentException.PaymentDuplicated;
import com.prgrms.monthsub.module.payment.dto.PaymentForm;
import com.prgrms.monthsub.module.payment.dto.PaymentPost;
import com.prgrms.monthsub.module.payment.dto.PaymentPost.Response;
import com.prgrms.monthsub.module.series.series.app.Provider.SeriesProvider;
import com.prgrms.monthsub.module.series.series.domain.ArticleUploadDate;
import com.prgrms.monthsub.module.series.series.domain.Series;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@EnableRetry
public class PaymentService {

  private final Logger log = LoggerFactory.getLogger(getClass());
  private final SeriesProvider seriesProvider;
  private final UserProvider userProvider;
  private final PaymentConverter paymentConverter;
  private final TransactionTemplate transactionTemplate;
  private final PaymentRepository paymentRepository;

  public PaymentService(
    SeriesProvider seriesProvider,
    UserProvider userProvider,
    PaymentConverter paymentConverter,
    TransactionTemplate transactionTemplate,
    PaymentRepository paymentRepository
  ) {
    this.seriesProvider = seriesProvider;
    this.userProvider = userProvider;
    this.paymentConverter = paymentConverter;
    this.transactionTemplate = transactionTemplate;
    this.paymentRepository = paymentRepository;
  }

  @Transactional
  public PaymentForm.Response getSeriesById(Long seriesId) {

    Series series = this.seriesProvider.getById(seriesId);
    List<ArticleUploadDate> uploadDateList = this.seriesProvider.getArticleUploadDate(seriesId);

    return this.paymentConverter.seriesToPaymentWindowResponse(series, uploadDateList);

  }

  @Retryable(maxAttempts = 3, value = ObjectOptimisticLockingFailureException.class)
  public PaymentPost.Response pay(
    Long id,
    Long userId
  ) {
    try {
      return this.transactionTemplate.execute(status -> this.createPayment(id, userId));
    } catch (ObjectOptimisticLockingFailureException e) {
      log.info("충돌 감지 재시도: {}", e.getMessage());
      throw new ObjectOptimisticLockingFailureException("충돌", Throwable.class);
    }
  }

  @Transactional
  public Response createPayment(
    Long seriesId,
    Long userId
  ) throws ObjectOptimisticLockingFailureException {

    Series series = this.seriesProvider.getById(seriesId);
    List<ArticleUploadDate> uploadDateList = this.seriesProvider.getArticleUploadDate(seriesId);

    User user = this.userProvider.findById(userId);
    user.decreasePoint(series.getPrice());

    this.paymentRepository.findByUserIdAndSeriesId(userId, seriesId)
      .map(pay -> {
        throw new PaymentDuplicated("이미 결제되었습니다.");
      });

    this.paymentRepository.save(this.paymentConverter.paymentToEntity(series, user));

    return this.paymentConverter.paymentResponse(series, uploadDateList, user.getPoint());

  }
}
