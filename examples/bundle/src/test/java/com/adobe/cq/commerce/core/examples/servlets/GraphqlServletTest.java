/*******************************************************************************
 *
 *    Copyright 2020 Adobe. All rights reserved.
 *    This file is licensed to you under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License. You may obtain a copy
 *    of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software distributed under
 *    the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 *    OF ANY KIND, either express or implied. See the License for the specific language
 *    governing permissions and limitations under the License.
 *
 ******************************************************************************/

package com.adobe.cq.commerce.core.examples.servlets;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.scripting.SlingBindings;
import org.apache.sling.models.spi.ImplementationPicker;
import org.apache.sling.servlethelpers.MockRequestPathInfo;
import org.apache.sling.servlethelpers.MockSlingHttpServletRequest;
import org.apache.sling.servlethelpers.MockSlingHttpServletResponse;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.xss.XSSAPI;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import com.adobe.cq.commerce.core.components.internal.services.UrlProviderImpl;
import com.adobe.cq.commerce.core.components.models.categorylist.FeaturedCategoryList;
import com.adobe.cq.commerce.core.components.models.common.ProductListItem;
import com.adobe.cq.commerce.core.components.models.navigation.Navigation;
import com.adobe.cq.commerce.core.components.models.product.Asset;
import com.adobe.cq.commerce.core.components.models.product.Product;
import com.adobe.cq.commerce.core.components.models.product.Variant;
import com.adobe.cq.commerce.core.components.models.productcarousel.ProductCarousel;
import com.adobe.cq.commerce.core.components.models.productlist.ProductList;
import com.adobe.cq.commerce.core.components.models.productteaser.ProductTeaser;
import com.adobe.cq.commerce.core.components.models.searchresults.SearchResults;
import com.adobe.cq.commerce.core.components.services.UrlProvider;
import com.adobe.cq.commerce.core.search.internal.services.SearchFilterServiceImpl;
import com.adobe.cq.commerce.core.search.internal.services.SearchResultsServiceImpl;
import com.adobe.cq.commerce.graphql.client.GraphqlClient;
import com.adobe.cq.commerce.graphql.client.GraphqlRequest;
import com.adobe.cq.commerce.graphql.client.GraphqlResponse;
import com.adobe.cq.commerce.magento.graphql.CategoryTree;
import com.adobe.cq.commerce.magento.graphql.Query;
import com.adobe.cq.commerce.magento.graphql.gson.Error;
import com.adobe.cq.commerce.magento.graphql.gson.QueryDeserializer;
import com.adobe.cq.sightly.SightlyWCMMode;
import com.day.cq.wcm.api.LanguageManager;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.designer.Style;
import com.day.cq.wcm.msm.api.LiveRelationshipManager;
import com.day.cq.wcm.scripting.WCMBindingsConstants;
import com.google.common.base.Function;
import com.google.gson.reflect.TypeToken;
import io.wcm.testing.mock.aem.junit.AemContext;
import io.wcm.testing.mock.aem.junit.AemContextCallback;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GraphqlServletTest {

    @Rule
    public final AemContext context = createContext("/context/jcr-content.json");

    private static AemContext createContext(String contentPath) {
        return new AemContext(
            (AemContextCallback) context -> {
                // Load page structure
                context.load().json(contentPath, "/content");
                context.registerService(ImplementationPicker.class, new ResourceTypeImplementationPicker());

                UrlProviderImpl urlProvider = new UrlProviderImpl();
                urlProvider.activate(new MockUrlProviderConfiguration());
                context.registerService(UrlProvider.class, urlProvider);

                context.registerInjectActivateService(new SearchFilterServiceImpl());
                context.registerInjectActivateService(new SearchResultsServiceImpl());
            },
            ResourceResolverType.JCR_MOCK);
    }

    private static final String PAGE = "/content/page";
    private static final String PRODUCT_RESOURCE = PAGE + "/jcr:content/root/responsivegrid/product";
    private static final String PRODUCT_LIST_RESOURCE = PAGE + "/jcr:content/root/responsivegrid/productlist";
    private static final String PRODUCT_CAROUSEL_RESOURCE = PAGE + "/jcr:content/root/responsivegrid/productcarousel";
    private static final String PRODUCT_TEASER_RESOURCE = PAGE + "/jcr:content/root/responsivegrid/productteaser";
    private static final String RELATED_PRODUCTS_RESOURCE = PAGE + "/jcr:content/root/responsivegrid/relatedproducts";
    private static final String UPSELL_PRODUCTS_RESOURCE = PAGE + "/jcr:content/root/responsivegrid/upsellproducts";
    private static final String CROSS_SELL_PRODUCTS_RESOURCE = PAGE + "/jcr:content/root/responsivegrid/crosssellproducts";
    private static final String SEARCH_RESULTS_RESOURCE = PAGE + "/jcr:content/root/responsivegrid/searchresults";
    private static final String FEATURED_CATEGORY_LIST_RESOURCE = PAGE + "/jcr:content/root/responsivegrid/featuredcategorylist";
    private static final String NAVIGATION_RESOURCE = PAGE + "/jcr:content/root/responsivegrid/navigation";

    private static final String CIF_DAM_ROOT = "/content/dam/core-components-examples/library/cif-sample-assets/";

    private GraphqlServlet graphqlServlet;
    private MockSlingHttpServletRequest request;
    private MockSlingHttpServletResponse response;

    @Before
    public void setUp() throws ServletException {
        graphqlServlet = new GraphqlServlet();
        graphqlServlet.init();
        request = new MockSlingHttpServletRequest(null);
        response = new MockSlingHttpServletResponse();
    }

    @Test
    public void testGetRequestWithVariables() throws ServletException, IOException {
        String query = "query rootCategory($catId: Int!) {category(id: $catId){id,name,url_path}}";

        Map<String, Object> params = new HashMap<>();
        params.put("query", query);
        params.put("variables", Collections.singletonMap("catId", 2));
        params.put("operationName", "rootCategory");
        request.setParameterMap(params);

        graphqlServlet.doGet(request, response);
        String output = response.getOutputAsString();

        Type type = TypeToken.getParameterized(GraphqlResponse.class, Query.class, Error.class).getType();
        GraphqlResponse<Query, Error> graphqlResponse = QueryDeserializer.getGson().fromJson(output, type);
        CategoryTree category = graphqlResponse.getData().getCategory();

        Assert.assertEquals(2, category.getId().intValue());
    }

    @Test
    public void testPostRequestWithVariables() throws ServletException, IOException {
        String query = "query rootCategory($catId: Int!) {category(id: $catId){id,name,url_path}}";

        GraphqlRequest graphqlRequest = new GraphqlRequest(query);
        graphqlRequest.setVariables(Collections.singletonMap("catId", 2));
        graphqlRequest.setOperationName("rootCategory");
        String body = QueryDeserializer.getGson().toJson(graphqlRequest);
        request.setContent(body.getBytes());

        graphqlServlet.doPost(request, response);
        String output = response.getOutputAsString();

        Type type = TypeToken.getParameterized(GraphqlResponse.class, Query.class, Error.class).getType();
        GraphqlResponse<Query, Error> graphqlResponse = QueryDeserializer.getGson().fromJson(output, type);
        CategoryTree category = graphqlResponse.getData().getCategory();

        Assert.assertEquals(2, category.getId().intValue());
    }

    private Resource prepareModel(String resourcePath) throws ServletException {
        Page page = Mockito.spy(context.currentPage(PAGE));
        context.currentPage(page);
        context.currentResource(resourcePath);
        Resource resource = Mockito.spy(context.currentResource());

        GraphqlClient graphqlClient = new MockGraphqlClient();
        Mockito.when(resource.adaptTo(GraphqlClient.class)).thenReturn(graphqlClient);

        Resource pageContent = Mockito.spy(page.getContentResource());
        when(page.getContentResource()).thenReturn(pageContent);
        when(pageContent.adaptTo(GraphqlClient.class)).thenReturn(graphqlClient);

        Function<Resource, GraphqlClient> adapter = r -> r.getPath().equals(PAGE) ? graphqlClient : null;
        context.registerAdapter(Resource.class, GraphqlClient.class, adapter);

        // This sets the page attribute injected in the models with @Inject or @ScriptVariable
        SlingBindings slingBindings = (SlingBindings) context.request().getAttribute(SlingBindings.class.getName());
        slingBindings.setResource(resource);
        slingBindings.put(WCMBindingsConstants.NAME_CURRENT_PAGE, page);
        slingBindings.put(WCMBindingsConstants.NAME_PROPERTIES, resource.getValueMap());

        XSSAPI xssApi = mock(XSSAPI.class);
        when(xssApi.filterHTML(Mockito.anyString())).then(i -> i.getArgumentAt(0, String.class));
        slingBindings.put("xssApi", xssApi);

        Style style = mock(Style.class);
        when(style.get(Mockito.anyString(), Mockito.isA(Boolean.class))).then(i -> i.getArgumentAt(1, Boolean.class));
        when(style.get(Mockito.anyString(), Mockito.isA(Integer.class))).then(i -> i.getArgumentAt(1, Integer.class));
        slingBindings.put("currentStyle", style);

        SightlyWCMMode wcmMode = mock(SightlyWCMMode.class);
        when(wcmMode.isDisabled()).thenReturn(false);
        slingBindings.put("wcmmode", wcmMode);

        return resource;
    }

    @Test
    public void testProductModel() throws ServletException {
        prepareModel(PRODUCT_RESOURCE);

        MockRequestPathInfo requestPathInfo = (MockRequestPathInfo) context.request().getRequestPathInfo();
        requestPathInfo.setSelectorString("beaumont-summit-kit");

        Product productModel = context.request().adaptTo(Product.class);
        Assert.assertEquals("MH01", productModel.getSku());
        Assert.assertEquals(15, productModel.getVariants().size());

        // We make sure that all assets in the sample JSON response point to the DAM
        for (Asset asset : productModel.getAssets()) {
            Assert.assertTrue(asset.getPath().startsWith(CIF_DAM_ROOT));
        }
        for (Variant variant : productModel.getVariants()) {
            for (Asset asset : variant.getAssets()) {
                Assert.assertTrue(asset.getPath().startsWith(CIF_DAM_ROOT));
            }
        }
    }

    @Test
    public void testGroupedProductModel() throws ServletException {
        prepareModel(PRODUCT_RESOURCE);

        MockRequestPathInfo requestPathInfo = (MockRequestPathInfo) context.request().getRequestPathInfo();
        requestPathInfo.setSelectorString("set-of-sprite-yoga-straps");

        Product productModel = context.request().adaptTo(Product.class);
        Assert.assertEquals("24-WG085_Group", productModel.getSku());
        Assert.assertTrue(productModel.isGroupedProduct());
        Assert.assertEquals(3, productModel.getGroupedProductItems().size());

        // We make sure that all assets in the sample JSON response point to the DAM
        for (Asset asset : productModel.getAssets()) {
            Assert.assertTrue(asset.getPath().startsWith(CIF_DAM_ROOT));
        }
    }

    @Test
    public void testProductListModel() throws ServletException {
        prepareModel(PRODUCT_LIST_RESOURCE);

        // The category data is coming from magento-graphql-category.json
        MockRequestPathInfo requestPathInfo = (MockRequestPathInfo) context.request().getRequestPathInfo();
        requestPathInfo.setSelectorString("1");
        ProductList productListModel = context.request().adaptTo(ProductList.class);
        Assert.assertEquals("Outdoor Collection", productListModel.getTitle());

        // The products are coming from magento-graphql-category-products.json
        Assert.assertEquals(6, productListModel.getProducts().size());

        // We make sure that all assets in the sample JSON response point to the DAM
        for (ProductListItem product : productListModel.getProducts()) {
            Assert.assertTrue(product.getImageURL().startsWith(CIF_DAM_ROOT));
        }
    }

    @Test
    public void testProductCarouselModel() throws ServletException {
        Resource resource = prepareModel(PRODUCT_CAROUSEL_RESOURCE);

        String[] productSkuList = (String[]) resource.getValueMap().get("product"); // The HTL script uses an alias here
        SlingBindings slingBindings = (SlingBindings) context.request().getAttribute(SlingBindings.class.getName());
        slingBindings.put("productSkuList", productSkuList);

        ProductCarousel productCarouselModel = context.request().adaptTo(ProductCarousel.class);
        Assert.assertEquals(4, productCarouselModel.getProducts().size());
        Assert.assertEquals("24-MB02", productCarouselModel.getProducts().get(0).getSKU());

        // We make sure that all assets in the sample JSON response point to the DAM
        for (ProductListItem product : productCarouselModel.getProducts()) {
            Assert.assertTrue(product.getImageURL().startsWith(CIF_DAM_ROOT));
        }
    }

    @Test
    public void testProductTeaserModel() throws ServletException {
        prepareModel(PRODUCT_TEASER_RESOURCE);
        ProductTeaser productTeaserModel = context.request().adaptTo(ProductTeaser.class);
        Assert.assertEquals("Summit Watch", productTeaserModel.getName());

        // We make sure that all assets in the sample JSON response point to the DAM
        Assert.assertTrue(productTeaserModel.getImage().startsWith(CIF_DAM_ROOT));
    }

    @Test
    public void testRelatedProductsModel() throws ServletException {
        prepareModel(RELATED_PRODUCTS_RESOURCE);
        ProductCarousel relatedProductsModel = context.request().adaptTo(ProductCarousel.class);
        Assert.assertEquals(3, relatedProductsModel.getProducts().size());
        Assert.assertEquals("24-MB01", relatedProductsModel.getProducts().get(0).getSKU());
    }

    @Test
    public void testUpsellProductsModel() throws ServletException {
        prepareModel(UPSELL_PRODUCTS_RESOURCE);
        ProductCarousel relatedProductsModel = context.request().adaptTo(ProductCarousel.class);

        // We test the SKUs to make sure we return the right response for UPSELL_PRODUCTS
        List<ProductListItem> products = relatedProductsModel.getProducts();
        Assert.assertEquals(2, products.size());
        Assert.assertEquals("24-MG03", products.get(0).getSKU());
        Assert.assertEquals("24-WG01", products.get(1).getSKU());

        // We make sure that all assets in the sample JSON response point to the DAM
        for (ProductListItem product : products) {
            Assert.assertTrue(product.getImageURL().startsWith(CIF_DAM_ROOT));
        }
    }

    @Test
    public void testCrosssellProductsModel() throws ServletException {
        prepareModel(CROSS_SELL_PRODUCTS_RESOURCE);
        ProductCarousel relatedProductsModel = context.request().adaptTo(ProductCarousel.class);
        Assert.assertEquals(3, relatedProductsModel.getProducts().size());
        Assert.assertEquals("24-MB01", relatedProductsModel.getProducts().get(0).getSKU());
    }

    @Test
    public void testSearchResultsModel() throws ServletException {
        prepareModel(SEARCH_RESULTS_RESOURCE);
        context.request().setParameterMap(Collections.singletonMap("search_query", "beaumont"));
        SearchResults searchResultsModel = context.request().adaptTo(SearchResults.class);

        Collection<ProductListItem> products = searchResultsModel.getProducts();
        Assert.assertEquals(6, products.size());
        // We make sure that all assets in the sample JSON response point to the DAM
        for (ProductListItem product : products) {
            Assert.assertTrue(product.getImageURL().startsWith(CIF_DAM_ROOT));
        }
    }

    @Test
    public void testFeaturedCategoryListModel() throws ServletException {
        prepareModel(FEATURED_CATEGORY_LIST_RESOURCE);
        FeaturedCategoryList featureCategoryListModel = context.request().adaptTo(FeaturedCategoryList.class);
        List<CategoryTree> categories = featureCategoryListModel.getCategories();
        Assert.assertEquals(3, categories.size());

        // Test that the Servlet didn't return 3 times the catalog category tree
        Assert.assertEquals(4, categories.get(0).getId().intValue());
        Assert.assertEquals(5, categories.get(1).getId().intValue());
        Assert.assertEquals(6, categories.get(2).getId().intValue());
    }

    @Test
    public void testNavigationModel() throws ServletException {
        prepareModel(NAVIGATION_RESOURCE);

        // Mock OSGi services for the WCM Navigation component
        context.registerService(LanguageManager.class, Mockito.mock(LanguageManager.class));
        context.registerService(LiveRelationshipManager.class, Mockito.mock(LiveRelationshipManager.class));

        Navigation navigationModel = context.request().adaptTo(Navigation.class);
        Assert.assertEquals(7, navigationModel.getItems().size()); // Our test catalog has 7 top-level categories
    }
}
