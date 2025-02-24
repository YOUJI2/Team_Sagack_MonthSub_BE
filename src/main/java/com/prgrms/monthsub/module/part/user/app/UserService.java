package com.prgrms.monthsub.module.part.user.app;

import com.prgrms.monthsub.common.s3.S3Client;
import com.prgrms.monthsub.common.s3.config.S3.Bucket;
import com.prgrms.monthsub.module.part.user.app.provider.UserProvider;
import com.prgrms.monthsub.module.part.user.converter.UserConverter;
import com.prgrms.monthsub.module.part.user.domain.User;
import com.prgrms.monthsub.module.part.user.domain.exception.UserException.EmailDuplicated;
import com.prgrms.monthsub.module.part.user.domain.exception.UserException.NickNameDuplicated;
import com.prgrms.monthsub.module.part.user.domain.exception.UserException.UserNotFound;
import com.prgrms.monthsub.module.part.user.dto.UserEdit;
import com.prgrms.monthsub.module.part.user.dto.UserSignUp;
import com.prgrms.monthsub.module.worker.explusion.domain.Expulsion.DomainType;
import com.prgrms.monthsub.module.worker.explusion.domain.Expulsion.FileCategory;
import com.prgrms.monthsub.module.worker.explusion.domain.Expulsion.FileType;
import com.prgrms.monthsub.module.worker.explusion.domain.Expulsion.Status;
import com.prgrms.monthsub.module.worker.explusion.domain.ExpulsionService;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class UserService implements UserProvider {

  private final PasswordEncoder passwordEncoder;
  private final UserRepository userRepository;
  private final ExpulsionService expulsionService;
  private final S3Client s3Client;
  private final UserConverter userConverter;

  public UserService(
    PasswordEncoder passwordEncoder,
    UserRepository userRepository,
    S3Client s3Client,
    UserConverter userConverter,
    ExpulsionService expulsionService
  ) {
    this.passwordEncoder = passwordEncoder;
    this.userRepository = userRepository;
    this.s3Client = s3Client;
    this.userConverter = userConverter;
    this.expulsionService = expulsionService;
  }

  @Override
  public Optional<User> findByNickname(String nickname) {
    return this.userRepository.findByNickname(nickname);
  }

  @Override
  public User findById(Long userId) {
    return this.userRepository
      .findById(userId)
      .orElseThrow(() -> new UserNotFound("id=" + userId));
  }

  public User findByEmail(String email) {
    return this.userRepository
      .findByEmail(email)
      .orElseThrow(() -> new UserNotFound("email=" + email));
  }

  public User login(
    String email,
    String credentials
  ) {
    User user = this.findByEmail(email);
    user.checkPassword(this.passwordEncoder, credentials);

    return user;
  }

  @Transactional
  public UserSignUp.Response signUp(UserSignUp.Request request) {
    checkEmail(request.email());
    checkNickName(request.nickName());
    User user = this.userRepository.save(this.userConverter.toEntity(request));

    return new UserSignUp.Response(user.getId());
  }

  @Transactional
  public UserEdit.Response edit(
    Long id,
    UserEdit.Request request,
    Optional<MultipartFile> image
  ) {
    checkNickName(request.nickName(), id);
    User user = this.findById(id);

    image.map(multipartFile -> this.uploadProfileImage(multipartFile, user));
    user.editUser(request.nickName(), request.profileIntroduce());

    return new UserEdit.Response(this.userRepository.save(user).getId());
  }
  
  @Transactional
  public String uploadProfileImage(
    MultipartFile image,
    User user
  ) {
    if (image.isEmpty()) {
      return null;
    }

    String key =
      "users/" + user.getId().toString()
        + "/profile/"
        + UUID.randomUUID()
        + this.s3Client.getExtension(image);

    String profileKey = this.s3Client.upload(
      Bucket.IMAGE,
      image,
      key
    );

    String originalProfileKey = user.getProfileKey();

    if (originalProfileKey != null) {
      expulsionService.save(
        user.getId(),
        user.getId(),
        originalProfileKey,
        Status.CREATED,
        DomainType.USER,
        FileCategory.USER_PROFILE,
        FileType.IMAGE
      );
    }

    user.changeProfileKey(profileKey);

    return key;
  }

  private void checkEmail(String email) {
    this.userRepository
      .findByEmail(email)
      .map(user -> {
        throw new EmailDuplicated("email = " + email);
      });
  }

  private void checkNickName(String nickName) {
    this.userRepository
      .findByNickname(nickName)
      .map(user -> {
        throw new NickNameDuplicated("nickName = " + nickName);
      });
  }

  private void checkNickName(
    String nickName,
    Long id
  ) {
    this.userRepository.findByNickname(nickName)
      .map(user -> {
        if (!user.getId().equals(id)) {
          throw new NickNameDuplicated("nickName = " + nickName);
        }
        return null;
      });
  }

}
