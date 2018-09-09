/*
 * Copyright (c) 2018, vindell (https://github.com/vindell).
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.shiro.spring.boot.kisso.authc;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.biz.utils.WebUtils;
import org.apache.shiro.biz.web.filter.authc.AbstractAuthenticatingFilter;
import org.apache.shiro.spring.boot.kisso.token.KissoToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.baomidou.kisso.SSOHelper;
import com.baomidou.kisso.common.SSOConstants;
import com.baomidou.kisso.security.token.SSOToken;
import com.baomidou.kisso.web.handler.KissoDefaultHandler;
import com.baomidou.kisso.web.handler.SSOHandlerInterceptor;

/**
 * Kisso认证 (authentication)过滤器
 * @author ： <a href="https://github.com/vindell">vindell</a>
 */
public class KissoAuthenticatingFilter extends AbstractAuthenticatingFilter {

	private static final Logger LOG = LoggerFactory.getLogger(KissoAuthenticatingFilter.class);
	private SSOHandlerInterceptor handlerInterceptor;
	
	public KissoAuthenticatingFilter() {
		super();
	}
	
	@Override
	protected boolean onAccessDenied(ServletRequest request, ServletResponse response) throws Exception {
		
		// 1、判断是否登录请求 
		if (isLoginRequest(request, response)) {
			
			HttpServletRequest httpRequest = WebUtils.toHttp(request);
			HttpServletResponse httpResponse = WebUtils.toHttp(response);
			
	        SSOToken ssoToken = SSOHelper.getSSOToken(httpRequest);
	        
	        if (ssoToken == null) {
	        	
	        	// Ajax 请求：响应json数据对象
	 			if (WebUtils.isAjaxRequest(request)) {
	 				
	 				if(this.getHandlerInterceptor() != null) {
		        		/*
		                 * Handler 处理 AJAX 请求
						 */
		                this.getHandlerInterceptor().preTokenIsNullAjax(httpRequest, httpResponse);
		                return false;
		        	}
	 				
	 				WebUtils.writeJSONString(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthentication.");
	 				return false;
	 			}
	 			
	 			if(this.getHandlerInterceptor() != null) {
	 				/*
					 * token 为空，调用 Handler 处理
					 * 返回 true 继续执行，清理登录状态并重定向至登录界面
					 */
	                if (this.getHandlerInterceptor().preTokenIsNull(httpRequest, httpResponse)) {
	                    LOG.debug("logout. request url:" + httpRequest.getRequestURL());
	                    SSOHelper.clearRedirectLogin(httpRequest, httpResponse);
	                }
	                return false;
	 			}
	 			
	 			// 普通请求：重定向到登录页
	 			saveRequestAndRedirectToLogin(request, response);
	 			return false;
	 			
	        } else {
	        	
	        	/*
				 * 正常请求，request 设置 token 减少二次解密
				 */
	            request.setAttribute(SSOConstants.SSO_TOKEN_ATTR, ssoToken);
	            
	            if (LOG.isTraceEnabled()) {
					LOG.trace("Login submission detected.  Attempting to execute login.");
				}
				return executeLogin(request, response);
				
			}
			 
		}
		// 2、未授权情况
		else {
			
			String mString = "Attempting to access a path which requires authentication. ";
			if (LOG.isTraceEnabled()) { 
				LOG.trace(mString);
			}
			
			// Ajax 请求：响应json数据对象
			if (WebUtils.isAjaxRequest(request)) {
				WebUtils.writeJSONString(response, HttpServletResponse.SC_UNAUTHORIZED, mString);
				return false;
			}
			// 普通请求：重定向到登录页
			saveRequestAndRedirectToLogin(request, response);
			return false;
		}
	}
	
	@Override
	protected AuthenticationToken createToken(String username, String password, ServletRequest request,
			ServletResponse response) {
			
		SSOToken ssoToken = SSOHelper.attrToken(WebUtils.toHttp(request));
		
		KissoToken token = new KissoToken(getHost(request), ssoToken);

		return token;
	}
	
	public SSOHandlerInterceptor getHandlerInterceptor() {
        if (handlerInterceptor == null) {
            return KissoDefaultHandler.getInstance();
        }
        return handlerInterceptor;
    }

    public void setHandlerInterceptor(SSOHandlerInterceptor handlerInterceptor) {
        this.handlerInterceptor = handlerInterceptor;
    }

}
