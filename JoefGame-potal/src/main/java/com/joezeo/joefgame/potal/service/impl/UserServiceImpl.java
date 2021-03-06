package com.joezeo.joefgame.potal.service.impl;

import com.joezeo.joefgame.common.dto.PaginationDTO;
import com.joezeo.joefgame.common.dto.SteamAppDTO;
import com.joezeo.joefgame.common.dto.UserDTO;
import com.joezeo.joefgame.common.enums.CustomizeErrorCode;
import com.joezeo.joefgame.common.enums.SolrCoreNameEnum;
import com.joezeo.joefgame.common.enums.SteamAppTypeEnum;
import com.joezeo.joefgame.common.exception.CustomizeException;
import com.joezeo.joefgame.common.exception.ServiceException;
import com.joezeo.joefgame.common.provider.UCloudProvider;
import com.joezeo.joefgame.common.utils.AvatarGenerator;
import com.joezeo.joefgame.common.utils.PasswordHelper;
import com.joezeo.joefgame.dao.mapper.*;
import com.joezeo.joefgame.dao.pojo.*;
import com.joezeo.joefgame.potal.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.RowBounds;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.ShiroException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@Service
@Slf4j
public class UserServiceImpl implements UserService {
    @Autowired
    private UCloudProvider uCloudProvider;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UserFavoriteAppMapper userFavoriteAppMapper;
    @Autowired
    private SteamAppInfoMapper steamAppInfoMapper;
    @Autowired
    private PasswordHelper passwordHelper;
    @Autowired
    private UserRoleMapper userRoleMapper;
    @Autowired
    private UserFollowMapper userFollowMapper;

    @Autowired
    private SolrClient solrClient;

    @Override
    public boolean isExistGithubUser(String githubID) {
        UserExample userExample = new UserExample();
        userExample.createCriteria().andGithubAccountIdEqualTo(githubID);
        List<User> memUser = userMapper.selectByExample(userExample);

        return (memUser != null && memUser.size() != 0);
    }

    @Override
    public boolean isExistSteamUser(String steamid) {
        UserExample userExample = new UserExample();
        userExample.createCriteria().andSteamIdEqualTo(steamid);
        List<User> memUser = userMapper.selectByExample(userExample);

        return (memUser != null && memUser.size() != 0);
    }

    @Override
    public void signup(User user) {
        passwordHelper.encryptPassword(user); // ????????????
        user.setGmtCreate(System.currentTimeMillis());
        user.setGmtModify(user.getGmtCreate());

        // ??????????????????
        String avatarUrl = null;
        try{
            avatarUrl = randomAvatar();
        } catch (ServiceException e){
            log.error(e.getMessage());
            throw new CustomizeException(CustomizeErrorCode.GENERATE_RANDOM_AVATAR_FAILED);
        }
        user.setAvatarUrl(avatarUrl);

        // ????????????????????????
        user.setBio("???????????????????????????????????????");

        int count = userMapper.insertSelective(user);
        if (count != 1) {
            log.error("??????createOrUpdate????????????????????????");
            throw new CustomizeException(CustomizeErrorCode.CREATE_NEW_USER_FAILD);
        }

        /*
        ????????????????????????????????????Solr???
         */
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);
        userDTO.setUserid(user.getId() + "");

        String coreName = SolrCoreNameEnum.USER.getName();
        try {
            solrClient.addBean(coreName, userDTO);
            solrClient.commit(coreName);
        } catch (IOException e) {
            log.error("??????Solr???????????????[core name:" + coreName + "]" +
                    "[steamApp:+" + userDTO.toString() + "+]");
            log.error("StackTrace:" + e.getStackTrace());
        } catch (SolrServerException e) {
            log.error("??????Solr???????????????[core name:" + coreName + "]" +
                    "[steamApp:+" + userDTO.toString() + "+]");
            log.error("StackTrace:" + e.getStackTrace());
        }

    }

    @Override
    public void loginBaseGithub(String githubID) {
        // ??????Shiro??????
        // ?????????????????????????????????????????????
        UsernamePasswordToken shiroToken = new UsernamePasswordToken("3-part-login", githubID, true);
        Subject subject = SecurityUtils.getSubject();
        try {
            subject.login(shiroToken);
        } catch (ShiroException e) {
            log.error("????????????Github???????????????????????????id:" + githubID);
            throw new CustomizeException(CustomizeErrorCode.USER_GITHUB_LOGIN_FALIED);
        }
    }

    @Override
    public void loginBaseSteam(String steamid) {
        // ??????Shiro??????
        // ?????????????????????????????????????????????
        UsernamePasswordToken shiroToken = new UsernamePasswordToken("3-part-login", steamid, true);
        Subject subject = SecurityUtils.getSubject();
        try {
            subject.login(shiroToken);
        } catch (ShiroException e) {
            log.error("????????????Steam???????????????????????????id:" + steamid);
            throw new CustomizeException(CustomizeErrorCode.USER_STEAM_LOGIN_FALIED);
        }
    }

    @Override
    public void login(User user, boolean isRemember) {
        UsernamePasswordToken shiroToken = new UsernamePasswordToken(user.getEmail(), user.getPassword(), isRemember);
        Subject subject = SecurityUtils.getSubject();
        try {
            subject.login(shiroToken);
        } catch (ShiroException e) {
            log.error("??????????????????,???????????????email???" + user.getEmail());
            throw new CustomizeException(CustomizeErrorCode.USER_LOGIN_FALIED);
        }

    }

    @Override
    public void logout() {
        Subject subject = SecurityUtils.getSubject();
        subject.logout();
    }


    @Override
    public boolean checkEmail(String targetEmail) {
        UserExample example = new UserExample();
        example.createCriteria().andEmailEqualTo(targetEmail);
        List<User> users = userMapper.selectByExample(example);
        if (users.size() != 0) {
            return true;
        }
        return false;
    }

    @Override
    public UserDTO queryUserByEmail(String email) {
        UserDTO userDTO = new UserDTO();

        // ??????????????????token???=null ???????????????????????????
        UserExample userExample = new UserExample();
        userExample.createCriteria().andEmailEqualTo(email);
        List<User> user = userMapper.selectByExample(userExample);
        if (user == null || user.size() == 0) {
            return null;
        }
        BeanUtils.copyProperties(user.get(0), userDTO);

        // ????????????????????????
        List<Role> roles = userRoleMapper.selectRolesById(userDTO.getId());
        List<String> names = roles.stream().map(role -> role.getName()).collect(Collectors.toList());
        userDTO.setRoles(names);

        // ???????????????null
        userDTO.setPassword(null);

        return userDTO;
    }

    @Override
    public UserDTO queryByAccountid(String accountId) {
        UserDTO userDTO = new UserDTO();
        if (accountId == null || "".equals(accountId)) {
            log.error("??????queryByAccoundid?????????accountId??????");
            throw new ServiceException("????????????");
        }

        UserExample userExample = new UserExample();
        userExample.createCriteria().andGithubAccountIdEqualTo(accountId);
        List<User> user = userMapper.selectByExample(userExample);
        if (user == null) {
            log.error("??????queryByAccountid?????????user??????????????????accounid???????????????????????????????????????");
            throw new ServiceException("??????user??????");
        }

        BeanUtils.copyProperties(user.get(0), userDTO);

        // ????????????????????????
        List<Role> roles = userRoleMapper.selectRolesById(userDTO.getId());
        List<String> names = roles.stream().map(role -> role.getName()).collect(Collectors.toList());
        userDTO.setRoles(names);

        // ??????????????????null
        userDTO.setPassword(null);
        return userDTO;
    }

    @Override
    public User queryByUserid(Long userid) {
        User user = userMapper.selectByPrimaryKey(userid);
        if (user == null) {
            log.error("??????????????????user??????,userid=" + userid);
            throw new CustomizeException(CustomizeErrorCode.SERVER_ERROR);
        }

        // ????????????salt?????????null
        user.setPassword(null);
        user.setSalt(null);
        return user;
    }

    @Override
    public PaginationDTO<UserDTO> listFollowUser(Long userid, Integer page) {
        PaginationDTO<UserDTO> paginationDTO = new PaginationDTO();

        // ?????????????????????
        UserFollowExample example = new UserFollowExample();
        example.createCriteria().andUseridEqualTo(userid);
        int count = (int) userFollowMapper.countByExample(example);

        paginationDTO.setPagination(page, 5, count); // ????????????5??????????????????
        page = paginationDTO.getPage(); // ??????page???????????????
        int index = (page - 1) * 5;

        List<UserFollow> userFollows = userFollowMapper.selectByExampleWithRowbounds(example, new RowBounds(index, 5));

        List<UserDTO> list = new ArrayList<>();
        userFollows.stream().forEach(item -> {
            User user = userMapper.selectByPrimaryKey(item.getId());
            UserDTO userDTO = new UserDTO();
            BeanUtils.copyProperties(user, userDTO);
            userDTO.setPassword(null);
            list.add(userDTO);
        });

        paginationDTO.setDatas(list);

        return paginationDTO;
    }

    @Override
    public PaginationDTO<SteamAppDTO> listFavoriteApp(Long userid, Integer page) {
        PaginationDTO<SteamAppDTO> paginationDTO = new PaginationDTO<>();

        // ?????????????????????
        UserFavoriteAppExample example = new UserFavoriteAppExample();
        example.createCriteria().andUseridEqualTo(userid);
        int count = (int) userFavoriteAppMapper.countByExample(example);

        paginationDTO.setPagination(page, 5, count); // ????????????5????????????Steam??????
        page = paginationDTO.getPage(); // ??????page???????????????

        int index = (page - 1) * 5;

        List<UserFavoriteApp> favoriteApps = userFavoriteAppMapper.selectByExampleWithRowbounds(example, new RowBounds(index, 5));

        List<SteamAppDTO> list = new ArrayList<>();
        favoriteApps.stream().forEach(favorite -> {
            SteamAppInfo steamAppInfo = steamAppInfoMapper.selectByAppid(favorite.getAppid(), SteamAppTypeEnum.typeOf(favorite.getType()));
            SteamAppDTO steamAppDTO = new SteamAppDTO();
            BeanUtils.copyProperties(steamAppInfo, steamAppDTO);
            list.add(steamAppDTO);
        });

        paginationDTO.setDatas(list);

        return paginationDTO;
    }

    @Override
    public List<UserDTO> listAllFollowUser(Long userid) {
        UserFollowExample example = new UserFollowExample();
        List<UserFollow> userFollows = userFollowMapper.selectByExample(example);

        List<UserDTO> list = new ArrayList<>();
        userFollows.stream().forEach(item -> {
            User user = userMapper.selectByPrimaryKey(item.getId());
            UserDTO userDTO = new UserDTO();
            BeanUtils.copyProperties(user, userDTO);
            userDTO.setPassword(null);
            list.add(userDTO);
        });
        return list;
    }

    @Override
    public String updateAvatar(MultipartFile avatar, Long userid, String oldAvatarUrl) {
        InputStream inputStream = null;
        try {
            inputStream = avatar.getInputStream();
        } catch (IOException e) {
            log.error("???MultipartFile????????????????????????????????????");
            throw new CustomizeException(CustomizeErrorCode.UPLOAD_AVATAR_FAILED);
        }

        String randomName = "avatar-" + UUID.randomUUID().toString();
        String avatarUrl = uCloudProvider.uploadAvatar(inputStream, "image/png", randomName);

        User user = new User();
        user.setId(userid);
        user.setAvatarUrl(avatarUrl);
        user.setGmtModify(System.currentTimeMillis());

        int res = userMapper.updateByPrimaryKeySelective(user);
        if(res != 1){
            log.error("???????????????????????????????????????:user=" + user);
            throw new CustomizeException(CustomizeErrorCode.UPLOAD_AVATAR_FAILED);
        }

        // ???uCloud??????????????????????????????
        uCloudProvider.deleteAvatar(oldAvatarUrl);

        return avatarUrl;
    }

    @Override
    public String updateAvatar(Long userid, String oldAvatarUrl) {
        String avatarUrl = null;
        try{
            avatarUrl = randomAvatar();
        } catch (ServiceException e){
            log.error(e.getMessage());
            throw new CustomizeException(CustomizeErrorCode.GENERATE_RANDOM_AVATAR_FAILED);
        }

        User user = new User();
        user.setId(userid);
        user.setAvatarUrl(avatarUrl);
        user.setGmtModify(System.currentTimeMillis());

        int res = userMapper.updateByPrimaryKeySelective(user);
        if(res != 1){
            log.error("???????????????????????????????????????:user=" + user);
            throw new CustomizeException(CustomizeErrorCode.UPLOAD_AVATAR_FAILED);
        }

        // ???uCloud??????????????????????????????
        uCloudProvider.deleteAvatar(oldAvatarUrl);

        return avatarUrl;
    }

    @Override
    public void updateBio(UserDTO userDTO) {
        if(userDTO == null){
            log.error("????????????userDTO?????????????????????null");
            throw new CustomizeException(CustomizeErrorCode.UPDATE_BIO_FAILED);
        }
        if(userDTO.getBio() == null || "".equals(userDTO.getBio())){
            log.error("????????????userDTO??????????????????" + userDTO);
            throw new CustomizeException(CustomizeErrorCode.UPDATE_BIO_FAILED);
        }

        User user = new User();
        user.setId(userDTO.getId());
        user.setBio(userDTO.getBio());
        user.setGmtModify(System.currentTimeMillis());
        int idx = userMapper.updateByPrimaryKeySelective(user);
        if(idx != 1){
            log.error("user??????????????????????????????" + user);
            throw new CustomizeException(CustomizeErrorCode.UPDATE_BIO_FAILED);
        }
    }

    /*----------------------private methods------------------------*/
    /**
     * ???????????????????????????
     * @return ???????????????uCloud???url??????
     */
    private String randomAvatar() throws ServiceException{
        try{ // openCV???????????? System.loadLibrary(Core.NATIVE_LIBRARY_NAME); ???spring-boot-devtools???????????????????????????????????????error
            // ??????????????????
            InputStream avatar = new AvatarGenerator().getARandomAvatar();
            return uCloudProvider.uploadAvatar(avatar, "image/jpeg", "avatar-" + UUID.randomUUID().toString() + ".jpg");
        } catch (Throwable e){ // ????????????error??????Throwable???????????????
            log.error("opencv??????????????????????????????spring-boot-devtools??????");
            throw new ServiceException("opencv??????????????????????????????spring-boot-devtools??????");
        }
    }
}
