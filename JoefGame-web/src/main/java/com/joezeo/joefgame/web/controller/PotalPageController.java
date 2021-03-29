package com.joezeo.joefgame.web.controller;

import com.joezeo.joefgame.common.dto.AccessTokenDTO;
import com.joezeo.joefgame.common.dto.GithubUser;
import com.joezeo.joefgame.common.dto.SteamUser;
import com.joezeo.joefgame.common.enums.CustomizeErrorCode;
import com.joezeo.joefgame.common.exception.CustomizeException;
import com.joezeo.joefgame.common.provider.GithubProvider;
import com.joezeo.joefgame.common.provider.SteamProvider;
import com.joezeo.joefgame.common.dto.UserDTO;
import com.joezeo.joefgame.potal.service.SteamService;
import com.joezeo.joefgame.potal.service.TopicService;
import com.joezeo.joefgame.potal.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

@Controller
public class PotalPageController {

    @Autowired
    private GithubProvider githubProvider;
    @Autowired
    private SteamProvider steamProvider;
    @Autowired
    private UserService userService;
    @Autowired
    private TopicService topicService;
    @Autowired
    private SteamService steamService;


    @Value("${github.client.id}")
    private String clientId;
    @Value("${github.client.secret}")
    private String clientSecret;
    @Value("${github.redirect.uri}")
    private String redirectUri;

    /**
     * 进行github三方验证登录
     * @return
     */
    @GetMapping("/githubLogin")
    public String githubLogin(){
        String url = "https://github.com/login/oauth/authorize?client_id="+clientId+"&scope=user&state=1";
        return "redirect:" + url;
    }

    /**
     * github
     * @param code github要求的接收参数
     * @param state 同上
     */
    @GetMapping("/callback")
    public String callback(@RequestParam("code") String code,
                           @RequestParam("state") String state,
                           HttpSession session) {
        AccessTokenDTO accessTokenDTO = new AccessTokenDTO();
        accessTokenDTO.setClient_id(clientId);
        accessTokenDTO.setClient_secret(clientSecret);
        accessTokenDTO.setCode(code);
        accessTokenDTO.setState(state);
        accessTokenDTO.setRedirect_uri(redirectUri);

        String accessToken = githubProvider.getAccessToken(accessTokenDTO);
        GithubUser githubUser = githubProvider.getGithubUser(accessToken);

        if (githubUser != null) {
            // 先进行检查数据库中是否已经有该条github用户数据
            // 如果有则登录，没有则将GithubUser信息存入session，转入注册页面
            boolean isExist = userService.isExistGithubUser(githubUser.getId());
            if(isExist){
                userService.loginBaseGithub(githubUser.getId());
                return "redirect:/";
            } else {
                session.setAttribute("tempGithubUser", githubUser);
                return "redirect:/signup";
            }

        } else { // githubUser == null 说明三方登录失败了，转回主页面
            return "redirect:/";
        }
    }

    @GetMapping("/steam/auth")
    public void auth(HttpServletResponse response){
        String destination = steamProvider.auth();
        if(destination != null){
            try {
                response.sendRedirect(destination);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @GetMapping("/steam/callback")
    public String steamCallback(HttpServletRequest request, HttpSession session){
        String steamid = steamProvider.getSteamid(request);
        // 操作steamID，判断当前用户是否注册，如未注册进入注册页面，然后与steamID绑定，如果已经注册则直接登录成功
        if(steamid != null && !"".equals(steamid)){
            boolean isExist = userService.isExistSteamUser(steamid);
            if(isExist){
                // 进行登录操作
                userService.loginBaseSteam(steamid);
                return "redirect:/";
            } else {
                // 通过steamID查询该用户的steam昵称
                SteamUser steamUser = new SteamUser();
                String steamName = steamProvider.getSteamName(steamid);
                steamUser.setPersonaname(steamName);
                steamUser.setSteamid(steamid);
                // 将steamUser存入session
                session.setAttribute("tempSteamUser", steamUser);
                // 转到注册页面
                return "redirect:/signup";
            }
        } else { // steamID 为空，说明steam 三方登录失败，转回主页面
            return "redirect:/";
        }
    }

    /**
     *  网站主欢迎页面
     */
    @GetMapping("/")
    public String htmIndex(HttpSession session) {
        UserDTO user = (UserDTO) session.getAttribute("user");
        if (user == null) {
            return "index";
        } else {
            return "redirect:/home";
        }
    }

    /**
     * 社区页面
     */
    @GetMapping("/forum")
    public String htmForum() {
        return "potal/forum";
    }

    /**
     *  loadding页面，为用户添加__access 验证cookie
     */
    @GetMapping("/loadding")
    public String htmLoadding() {
        return "loadding";
    }

    /**
     * login登录页面
     */
    @GetMapping("/login")
    public String htmLogin(){
        return "potal/login";
    }

    /**
     * signup注册页面
     */
    @GetMapping("/signup")
    public String htmSignup(){
        return "potal/signup";
    }

    /**
     * 图床页面
     */
    @GetMapping("/gallery")
    public String htmGallery() {
        return "potal/gallery";
    }

    /**
     *  个人主页页面
     */
    @GetMapping("/home")
    public String htmHome(){
        return "potal/home";
    }

    /**
     *  个人中心页面
     */
    @GetMapping("/profile/{action}")
    public String profile(HttpSession session) {
        UserDTO user = (UserDTO) session.getAttribute("user");
        if (user == null) {
            throw new CustomizeException(CustomizeErrorCode.USER_NOT_LOGIN);
        }

        return "potal/profile";
    }

    // 获取新增帖子页面
    @GetMapping("/publish")
    public String htmPublish(Model model) {
        return "potal/publish";
    }

    // 获取编辑帖子页面
    @GetMapping("/publish/{id}")
    public String htmEdit(@PathVariable(name = "id") Long id,
                          Model model) {
        model.addAttribute("id", id);
        return "potal/publish";
    }

    /**
     *  Steam app列表页面
     */
    @GetMapping("/steam/apps")
    public String htmApps(){
        return "potal/apps";
    }

    /**
     *  指定appid的steam app页面
     */
    @GetMapping("/steam/app/{appid}")
    public String htmApp(@PathVariable("appid") Integer appid){
        if(!steamService.isExist(appid)){ // 不存在抛异常
            throw new CustomizeException(CustomizeErrorCode.APP_NOT_FOUND);
        }
        return "potal/app";
    }

    /**
     *  获取指定id帖子页面
     */
    @GetMapping("/topic/{id}")
    public String topic(@PathVariable("id") Long id) {
        if(!topicService.isExist(id)){ // 不存在抛异常
            throw new CustomizeException(CustomizeErrorCode.TOPIC_NOT_FOUND);
        }
        return "potal/topic";
    }

    /**
     * 获取搜索结果页面
     */
    @GetMapping("/search/{type}")
    public String htmSearch (){
        return "potal/search";
    }
}
