package com.joezeo.joefgame.manager.spider;

import com.joezeo.joefgame.common.dto.SteamAppDTO;
import com.joezeo.joefgame.common.enums.SolrCoreNameEnum;
import com.joezeo.joefgame.common.mq.MessageSupplier;
import com.joezeo.joefgame.dao.mapper.*;
import com.joezeo.joefgame.dao.pojo.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@Service
@Slf4j
public class PageResolver {

    @Autowired
    private SteamUrlMapper steamUrlMapper;
    @Autowired
    private SteamAppInfoMapper steamAppInfoMapper;
    @Autowired
    private SteamSubBundleInfoMapper steamSubBundleInfoMapper;
    @Autowired
    private SteamHistoryPriceMapper steamHistoryPriceMapper;
    @Autowired
    private ProxyIPMapper proxyIPMapper;
    @Autowired
    private UserFavoriteAppMapper userFavoriteAppMapper;

    @Autowired
    private MessageSupplier messageSupplier;
    @Autowired
    private SolrClient solrClient;

    /**
     * 解析获取Steam各个搜索页的总页数
     */
    public int resolvSteamTotalPage(String page) {
        Document doc = Jsoup.parse(page);
        Elements searchDivs = doc.getElementsByClass("search_pagination_right");
        String pageStr = "";
        for (Element e : searchDivs) {
            Elements as = e.getElementsByTag("a");
            int i = 0;
            for (Element a : as) {
                if (i == 2) {
                    pageStr = a.html();
                    break;
                }
                i++;
            }
        }
        return Integer.parseInt(pageStr);
    }

    /**
     * 解析获取西刺代理页面的总页数
     */
    public int resolvIPTotalPage(String string) {
        Document doc = Jsoup.parse(string);

        Elements paginations = doc.getElementsByClass("pagination");
        int totalPage = 0;
        for (Element e : paginations) {
            Elements as = e.getElementsByTag("a");
            int index = 0;
            for (Element a : as) {
                if (index == 9) {
                    totalPage = Integer.parseInt(a.html());
                }
                index++;
            }
            break;
        }
        return totalPage;
    }

    /**
     * 解析西刺代理网页的代理ip
     */
    public void resolvProxyIP(String page) {
        Document doc = Jsoup.parse(page);

        String ip = "";
        Integer port = 0;
        String type = "";
        Double speed = 0d;
        Double connectTime = 0d;
        String survive = "";

        Element ipList = doc.getElementById("ip_list");
        Elements trs = ipList.getElementsByTag("tr");
        for (Element tr : trs) {
            Elements tds = tr.getElementsByTag("td");
            int index = 0;
            for (Element td : tds) {
                if (index == 1) { // ip地址
                    ip = td.html();
                } else if (index == 2) { // 端口号
                    port = Integer.parseInt(td.html());
                } else if (index == 5) { // 类型 HTTP/HTTPS
                    type = td.html();
                } else if (index == 6) { // 速度
                    Elements divs = td.getElementsByTag("div");
                    Element e = divs.get(0);
                    String speedStr = e.attr("title").replace("秒", "");
                    speed = Double.parseDouble(speedStr);
                } else if (index == 7) { //
                    Elements divs = td.getElementsByTag("div");
                    Element e = divs.get(0);
                    String connectStr = e.attr("title").replace("秒", "");
                    connectTime = Double.parseDouble(connectStr);
                } else if (index == 8) {
                    survive = td.html();
                }
                index++;
            }

            if (speed < 1 && connectTime < 1 && survive.indexOf("天") != -1) {// 速度小于1秒，连接时间小于1秒，存活天数至少为1天的才做记录
                // 查询此ip在数据库是否存在
                ProxyIPExample example = new ProxyIPExample();
                example.createCriteria().andAddressEqualTo(ip);
                List<ProxyIP> proxyIPS = proxyIPMapper.selectByExample(example);
                if (proxyIPS == null || proxyIPS.size() == 0) {// 如果不存在再插入此条代理ip
                    ProxyIP proxyip = new ProxyIP();
                    proxyip.setAddress(ip);
                    proxyip.setPort(port);
                    proxyip.setType(type);
                    proxyip.setSpeed(speed);
                    proxyip.setConnecttime(connectTime);
                    proxyip.setSurvive(survive);
                    proxyip.setGmtCreate(System.currentTimeMillis());
                    proxyip.setGmtModify(proxyip.getGmtCreate());
                    int idx = proxyIPMapper.insert(proxyip);
                    if (idx != 1) {
                        log.error("插入代理ip失败");
                    }
                }
            }
        }
    }

    /**
     * 解析特惠商品的价格，存入t_steam_history_price表中
     */
    public void dailySpideSpecialPrice(String url, String page, Integer appid) {
        Document doc = Jsoup.parse(page);

        if (doc.getElementById("error_box") != null) { // 此app中国地区不支持
            return;
        }

        Integer finalPrice = 0;
        Elements purchaseGameDiv = doc.getElementsByClass("game_area_purchase_game");
        for (Element ele : purchaseGameDiv) {
            Elements finDiv = ele.getElementsByClass("discount_final_price");
            if (finDiv.size() != 0) { // 降价了
                for (Element subele : finDiv) {
                    String finalPriceStrWithTag = subele.html();
                    String finalPriceStr = finalPriceStrWithTag.substring(finalPriceStrWithTag.lastIndexOf(" ") + 1);
                    finalPrice = Integer.parseInt(finalPriceStr.replaceAll(",", ""));
                    break;
                }
            }
            break;
        }
        SteamHistoryPrice historyPrice = new SteamHistoryPrice();
        historyPrice.setAppid(appid);
        historyPrice.setPrice(finalPrice);
        historyPrice.setGmtCreate(System.currentTimeMillis());

        String type = "";
        if (url.lastIndexOf("/bundle/") != -1) {
            type = "bundle";
        } else if (url.lastIndexOf("/sub/") != -1) {
            type = "sub";
        } else {
            type = "app";
        }
        historyPrice.setType(type);
        int index = steamHistoryPriceMapper.insert(historyPrice);
        if (index != 1) {
            log.error("存储特惠价格失败，appid=" + appid);
        }

        /*
        将该应用的特惠信息存入消息队列中
         */
        // 获取收藏该App的用户idlist
        UserFavoriteAppExample example = new UserFavoriteAppExample();
        example.createCriteria().andAppidEqualTo(appid);
        List<UserFavoriteApp> list = userFavoriteAppMapper.selectByExample(example);

        // 获取该App的steamAppInfo
        SteamAppInfo steamAppInfo = steamAppInfoMapper.selectByAppid(appid, type);

        // 使用 messageSupplier 存储消息至消息队列
        for (UserFavoriteApp userApp : list) {
            messageSupplier.putMessage("" + appid, "" + userApp.getId(), steamAppInfo);
        }

        /*
        将降价的app信息存入Solr中
         */
        SteamAppDTO steamAppDTO = new SteamAppDTO();
        BeanUtils.copyProperties(steamAppInfo, steamAppDTO);
        steamAppDTO.setId("" + steamAppInfo.getId());

        String coreName = SolrCoreNameEnum.nameOf(steamAppDTO.getAppType());
        try {
            solrClient.addBean(coreName, steamAppDTO);
            solrClient.commit(coreName);
        } catch (IOException e) {
            log.error("更新Solr数据失败：[core name:"+coreName+"]" +
                    "[steamApp:+"+steamAppDTO.toString()+"+]");
            log.error("StackTrace:" + e.getStackTrace());
        } catch (SolrServerException e) {
            log.error("更新Solr数据失败：[core name:"+coreName+"]" +
                    "[steamApp:+"+steamAppDTO.toString()+"+]");
            log.error("StackTrace:" + e.getStackTrace());
        }
    }

    /**
     * 解析steam搜索页面，获取各个app的url
     * 用于日常/初始化爬取搜索页面
     * 与数据库中的做对比，如果没有的则插入
     */
    public void initOrCheckUrl(String page, String type) {
        Document doc = Jsoup.parse(page);

        // 从doc对象获取数据
        Element content = doc.getElementById("search_resultsRows");
        Elements links = content.getElementsByTag("a");
        links.stream().forEach(link -> {
            String appKey = link.attr("data-ds-itemkey");// ep:App_901583
            String appid = appKey.substring(appKey.lastIndexOf("_") + 1);

            String url = link.attr("href");
            // steam上每天搜索页的app都会加上一个不同的参数，如?snr=1_7_7_230_150_1364，存储时去掉这个参数
            url = url.substring(0, url.lastIndexOf("?"));

            List<SteamUrl> urlList = steamUrlMapper.selectByAppid(Integer.parseInt(appid), type);
            if (urlList == null || urlList.size() == 0) {
                // 说明该app地址不存在,存入数据库中
                int index = steamUrlMapper.insert(appid, url, type);
                if (index < 0) {
                    log.error("url地址存储数据库时失败,appid=" + appid);
                }
            } else if (urlList.size() == 1) {
                String memUrl = urlList.get(0).getUrl();
                String newUrl = url;
                if (!memUrl.equals(newUrl)) { // 因为steam的礼包（sub）和软件（app）的appid有可能相同,但是url不同
                    int index = steamUrlMapper.insert(appid, url, type);
                    if (index != 1) {
                        log.error("存储App Url失败,appid=" + appid);
                    }
                }
            }
        });
    }

    /**
     * 解析app页面，获取软件的详细信息
     * 初始化/日常检查app信息是否存在缺漏，需要从数据库中检查该app是否已经存在
     *
     * @Param isSub 该页面是否是关于礼包的
     */
    public void initOrCheckAppInfo(String page, String type, Integer appid, boolean isSub) {
        if (isSub || "sub".equals(type) || "bundle".equals(type)) { // 这里判断两个条件是因为在app列表中可能混入sub
            if (isSub) {
                type = "sub";
            }
            resolvSubOrBundle(page, appid, type);
            return;
        }

        Document doc = Jsoup.parse(page);

        if (doc.getElementById("error_box") != null) { // 此app中国地区不支持
            return;
        }

        // 软件名称
        String appName = "";
        Elements nameDiv = doc.getElementsByClass("apphub_AppName");
        appName = nameDiv.stream().map(name -> name.html()).collect(Collectors.joining());

        // 介绍图地址
        String imgSrc = "";
        Elements imgDiv = doc.getElementsByClass("game_header_image_full");
        imgSrc = imgDiv.stream().map(img -> img.attr("src")).collect(Collectors.joining());

        // 软件简介
        String description = "";
        if ("dlc".equals(type)) { // dlc 的简介被另外class修饰
            Elements elements = doc.getElementsByClass("glance_details");
            for (Element ele : elements) {
                Elements ps = ele.getElementsByTag("p");
                for (Element p : ps) {
                    description = p.html();
                    break;
                }
                break;
            }
        } else {
            Elements descDiv = doc.getElementsByClass("game_description_snippet");
            description = descDiv.stream().map(desc -> desc.html()).collect(Collectors.joining());
        }

        // 发行日期
        String date = "";
        Elements dateDiv = doc.getElementsByClass("date");
        date = dateDiv.stream().map(datediv -> datediv.html()).collect(Collectors.joining());

        // 开发商
        String devlop = "";
        Element devpDiv = doc.getElementById("developers_list");
        if (devpDiv != null) {
            Elements devps = devpDiv.getElementsByTag("a");
            if (devps != null) {
                devlop = devps.stream().map(devp -> devp.html()).collect(Collectors.joining(";"));
            }
        }

        // 发行商
        String publisher = "";
        Elements divs = doc.getElementsByClass("summary column");
        for (Element div : divs) {
            Elements as = div.getElementsByTag("a");
            // 发行商刚好是最后一个
            if (as.size() != 0) { // 有些软件没有发行商
                publisher = as.stream().map(a -> a.html()).collect(Collectors.joining());
            } else {
                publisher = "";
            }
        }

        Integer originalPrice = 0;
        Integer finalPrice = 0;
        Elements wrapper = doc.getElementsByClass("game_area_purchase_game_wrapper");
        if (wrapper.size() == 0) { // 捆绑包没有这个wrapper,保险起见还是留着，以防有些app也没有这个
            Elements purchaseGameDiv = doc.getElementsByClass("game_area_purchase_game");
            for (Element ele : purchaseGameDiv) {
                Elements oriDiv = ele.getElementsByClass("discount_original_price");
                Elements finDiv = ele.getElementsByClass("discount_final_price");
                if (oriDiv.size() != 0) { // 降价了
                    for (Element subele : oriDiv) {
                        String oriPriceStrWithTag = subele.html();
                        String oriPriceStr = oriPriceStrWithTag.substring(oriPriceStrWithTag.lastIndexOf(" ") + 1);
                        originalPrice = Integer.parseInt(oriPriceStr.replaceAll(",", ""));
                        break;
                    }
                    for (Element subele : finDiv) {
                        String finalPriceStrWithTag = subele.html();
                        String finalPriceStr = finalPriceStrWithTag.substring(finalPriceStrWithTag.lastIndexOf(" ") + 1);
                        finalPrice = Integer.parseInt(finalPriceStr.replaceAll(",", ""));
                        break;
                    }
                } else { // 没有降价
                    Elements priceDiv = ele.getElementsByClass("game_purchase_price price");
                    for (Element subele : priceDiv) {
                        if (subele.html() == null || "免费游玩".equals(subele.html())
                                || "免费开玩".equals(subele.html())
                                || "免费".equals(subele.html())
                                || "".equals(subele.html())) {
                            originalPrice = 0;
                            finalPrice = 0;
                            break;
                        } else {
                            String priceStr = subele.attr("data-price-final");
                            if ("".equals(priceStr)) {
                                originalPrice = 0;
                                finalPrice = 0;
                                break;
                            } else {
                                originalPrice = Integer.parseInt(priceStr.replaceAll(",", "")) / 100;
                                finalPrice = originalPrice;
                                break;
                            }
                        }
                    }
                }
                break; // 第二个是捆绑包的价格
            }
        } else { // 非捆绑包的app是被这个wrapper包裹的
            for (Element wra : wrapper) {
                Elements purchaseGameDiv = wra.getElementsByClass("game_area_purchase_game");
                for (Element ele : purchaseGameDiv) {
                    Elements oriDiv = ele.getElementsByClass("discount_original_price");
                    Elements finDiv = ele.getElementsByClass("discount_final_price");
                    if (oriDiv.size() != 0) { // 降价了
                        for (Element subele : oriDiv) {
                            String oriPriceStrWithTag = subele.html();
                            String oriPriceStr = oriPriceStrWithTag.substring(oriPriceStrWithTag.lastIndexOf(" ") + 1);
                            originalPrice = Integer.parseInt(oriPriceStr.replaceAll(",", ""));
                            break;
                        }
                        for (Element subele : finDiv) {
                            String finalPriceStrWithTag = subele.html();
                            String finalPriceStr = finalPriceStrWithTag.substring(finalPriceStrWithTag.lastIndexOf(" ") + 1);
                            finalPrice = Integer.parseInt(finalPriceStr.replaceAll(",", ""));
                            break;
                        }
                    } else { // 没有降价
                        Elements priceDiv = ele.getElementsByClass("game_purchase_price price");
                        for (Element subele : priceDiv) {
                            if (subele.html() == null || "免费游玩".equals(subele.html())
                                    || "免费开玩".equals(subele.html())
                                    || "免费".equals(subele.html())
                                    || "".equals(subele.html())) {
                                originalPrice = 0;
                                finalPrice = 0;
                                break;
                            } else {
                                String priceStr = subele.attr("data-price-final");
                                if ("".equals(priceStr)) {
                                    originalPrice = 0;
                                    finalPrice = 0;
                                    break;
                                } else {
                                    originalPrice = Integer.parseInt(priceStr.replaceAll(",", "")) / 100;
                                    finalPrice = originalPrice;
                                    break;
                                }
                            }
                        }
                    }
                    break; // 第二个是捆绑包的价格
                }
                break;
            }
        }

        if (originalPrice == 0 && finalPrice == 0) {
            // 如果经过上面的解析价格依旧为0，则有可能steam使用了另外个标签
            // 价格可能没有被.game_purchase_price price包裹
            Elements blocks = doc.getElementsByClass("discount_block game_purchase_discount no_discount");
            if (blocks.size() != 0) {
                for (Element block : blocks) {
                    String priceStr = block.attr("data-price-final");
                    if (priceStr != null && !"".equals(priceStr)) {
                        originalPrice = Integer.parseInt(priceStr) / 100;
                        finalPrice = originalPrice;
                        break;
                    }
                }
            }
        }

        // 用户评测
        String summary = "";
        Elements summryDivs = doc.getElementsByClass("user_reviews_summary_row");
        summary = summryDivs.stream()
                .map(div -> div.attr("data-tooltip-html"))
                .collect(Collectors.joining("|"));

        SteamAppInfo steamAppInfo = steamAppInfoMapper.selectByAppid(appid, type);
        if (steamAppInfo == null || steamAppInfo.getAppid() == null) { // 该app不存在才存入数据库中
            steamAppInfo = new SteamAppInfo();
            steamAppInfo.setAppid(appid);
            steamAppInfo.setName(appName);
            steamAppInfo.setImgUrl(imgSrc);
            steamAppInfo.setDescription(description);
            steamAppInfo.setReleaseDate(date);
            steamAppInfo.setDevloper(devlop);
            steamAppInfo.setPublisher(publisher);
            steamAppInfo.setOriginalPrice(originalPrice);
            steamAppInfo.setFinalPrice(finalPrice);
            steamAppInfo.setSummary(summary);
            steamAppInfo.setGmtCreate(System.currentTimeMillis());
            steamAppInfo.setGmtModify(steamAppInfo.getGmtCreate());
            int index = steamAppInfoMapper.insert(steamAppInfo, type);
            if (index != 1) {
                log.error("存储app信息失败,appid=" + appid);
            } else {
                log.info("存储app信息成功,appid=" + appid);
            }

            // 将App信息存入Solr中
            SteamAppDTO steamAppDTO = new SteamAppDTO();
            BeanUtils.copyProperties(steamAppInfo, steamAppDTO);
            steamAppDTO.setId("" + steamAppDTO.getId());

            String coreName = SolrCoreNameEnum.nameOf(type);
            try {
                solrClient.addBean(coreName, steamAppDTO);
                solrClient.commit(coreName);
            } catch (IOException e) {
                log.error("新增Solr数据失败：[core name:"+coreName+"]" +
                        "[steamApp:+"+steamAppDTO.toString()+"+]");
                log.error("StackTrace:" + e.getStackTrace());
            } catch (SolrServerException e) {
                log.error("新增Solr数据失败：[core name:"+coreName+"]" +
                        "[steamApp:+"+steamAppDTO.toString()+"+]");
                log.error("StackTrace:" + e.getStackTrace());
            }
        }
    }

    /*
            解析礼包、捆绑包信息
     */
    private void resolvSubOrBundle(String page, Integer appid, String type) {
        Document doc = Jsoup.parse(page);

        if (doc.getElementById("error_box") != null) { // 此app中国地区不支持
            return;
        }

        // 礼包名称
        String name = "";
        Elements nameDivs = doc.getElementsByClass("page_title_area game_title_area");
        for (Element e : nameDivs) {
            Elements h2s = e.getElementsByTag("h2");
            for (Element h2 : h2s) {
                name = h2.html();
            }
        }

        // 开发商 发行商 发行时间
        String developer = "";
        String publisher = "";
        String date = "";
        Elements detailDiv = doc.getElementsByClass("details_block");
        for (Element ele : detailDiv) {
            Elements bs = ele.getElementsByTag("b");
            for (Element b : bs) {
                if ("开发商:".equals(b.html())) {
                    while (true) {
                        Element a = b.nextElementSibling();
                        if ("a".equals(a.tagName())) {
                            developer += a.html() + ",";
                        } else {
                            break;
                        }
                        b = a;
                    }
                } else if ("发行商:".equals(b.html())) {
                    while (true) {
                        Element a = b.nextElementSibling();
                        if ("a".equals(a.tagName())) {
                            publisher += a.html() + ",";
                        } else {
                            break;
                        }
                        b = a;
                    }
                } else if ("发行日期:".equals(b.html())) {
                    date = b.nextSibling().toString().trim();
                }
            }
        }
        // 去掉最后一个逗号
        if (!"".equals(developer)) {
            developer.substring(0, developer.length() - 1);
        }
        if (!"".equals(publisher)) {
            publisher.substring(0, publisher.length() - 1);
        }

        // 价格
        Integer originalPrice = 0;
        Integer finalPrice = 0;
        Elements purchaseGameDiv = doc.getElementsByClass("game_area_purchase_game");
        for (Element ele : purchaseGameDiv) {
            Elements oriDiv = ele.getElementsByClass("discount_original_price");
            Elements finDiv = ele.getElementsByClass("discount_final_price");
            if (oriDiv.size() != 0) { // 降价了
                for (Element subele : oriDiv) {
                    String oriPriceStrWithTag = subele.html();
                    String oriPriceStr = oriPriceStrWithTag.substring(oriPriceStrWithTag.lastIndexOf(" ") + 1);
                    originalPrice = Integer.parseInt(oriPriceStr.replaceAll(",", ""));
                    break;
                }
                for (Element subele : finDiv) {
                    String finalPriceStrWithTag = subele.html();
                    String finalPriceStr = finalPriceStrWithTag.substring(finalPriceStrWithTag.lastIndexOf(" ") + 1);
                    finalPrice = Integer.parseInt(finalPriceStr.replaceAll(",", ""));
                    break;
                }
            } else { // 没有降价
                Elements priceDiv = ele.getElementsByClass("game_purchase_price price");
                for (Element subele : priceDiv) {
                    if (subele.html() == null || "免费游玩".equals(subele.html())
                            || "免费开玩".equals(subele.html())
                            || "免费".equals(subele.html())
                            || "".equals(subele.html())) {
                        originalPrice = 0;
                        finalPrice = 0;
                        break;
                    } else {
                        String priceStr = subele.attr("data-price-final");
                        if ("".equals(priceStr)) {
                            originalPrice = 0;
                            finalPrice = 0;
                            break;
                        } else {
                            originalPrice = Integer.parseInt(priceStr.replaceAll(",", "")) / 100;
                            finalPrice = originalPrice;
                            break;
                        }
                    }
                }
            }
            break; // 第二个与第一个是相同的内容
        }

        if (originalPrice == 0 && finalPrice == 0) {
            // 如果经过上面的解析价格依旧为0，则有可能steam使用了另外个标签
            // 价格可能没有被.game_purchase_price price包裹
            Elements blocks = doc.getElementsByClass("discount_block game_purchase_discount no_discount");
            if (blocks.size() != 0) {
                for (Element block : blocks) {
                    String priceStr = block.attr("data-price-final");
                    originalPrice = Integer.parseInt(priceStr) / 100;
                    finalPrice = originalPrice;
                    break;
                }
            }
        }

        // 礼包包含的app的id 以,分隔
        String contains = "";
        Elements containsDivs = doc.getElementsByClass("tab_item");
        contains = containsDivs.stream()
                .map(div -> {
                    String appKey = div.attr("data-ds-itemkey"); // 如App_203510
                    String subappid = appKey.substring(appKey.lastIndexOf("_") + 1); // 取出后面的数字
                    return subappid;
                }).collect(Collectors.joining(","));

        // 礼包缩略图地址
        String imgUrl = "";
        Element packageHeader = doc.getElementById("package_header_container");
        Elements imgs = packageHeader.getElementsByTag("img");
        for (Element img : imgs) {
            imgUrl = img.attr("src");
        }

        SteamSubBundleInfo steamSubBundleInfo = steamSubBundleInfoMapper.selectByAppid(appid, type);
        if (steamSubBundleInfo == null || steamSubBundleInfo.getAppid() == null) { // 数据库中没有该礼包信息，则插入
            steamSubBundleInfo = new SteamSubBundleInfo();
            steamSubBundleInfo.setAppid(appid);
            steamSubBundleInfo.setName(name);
            steamSubBundleInfo.setDevloper(developer);
            steamSubBundleInfo.setPublisher(publisher);
            steamSubBundleInfo.setReleaseDate(date);
            steamSubBundleInfo.setOriginalPrice(originalPrice);
            steamSubBundleInfo.setFinalPrice(finalPrice);
            steamSubBundleInfo.setContains(contains);
            steamSubBundleInfo.setGmtCreate(System.currentTimeMillis());
            steamSubBundleInfo.setGmtModify(steamSubBundleInfo.getGmtCreate());
            steamSubBundleInfo.setImgUrl(imgUrl);
            steamSubBundleInfo.setType(type);
            if("sub".equals(type)){
                steamSubBundleInfo.setAppType(7);
            } else {
                steamSubBundleInfo.setAppType(5);
            }
            int index = steamSubBundleInfoMapper.insert(steamSubBundleInfo);
            if (index != 1) {
                log.error("插入steam礼包信息失败,appid" + appid);
            }

            // 将App信息存入Solr中
            SteamAppDTO steamAppDTO = new SteamAppDTO();
            BeanUtils.copyProperties(steamSubBundleInfo, steamAppDTO);
            steamAppDTO.setId("" + steamSubBundleInfo.getId());

            String coreName = SolrCoreNameEnum.STEAMAPP_SUBBUNDLE.getName();
            try {
                solrClient.addBean(coreName, steamAppDTO);
                solrClient.commit(coreName);
            } catch (IOException e) {
                log.error("新增Solr数据失败：[core name:"+coreName+"]" +
                        "[steamApp:+"+steamAppDTO.toString()+"+]");
                log.error("StackTrace:" + e.getStackTrace());
            } catch (SolrServerException e) {
                log.error("新增Solr数据失败：[core name:"+coreName+"]" +
                        "[steamApp:+"+steamAppDTO.toString()+"+]");
                log.error("StackTrace:" + e.getStackTrace());
            }
        }

    }

}
