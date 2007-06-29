package org.ofbiz.oagis;

/**
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
**/
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javolution.util.FastList;
import javolution.util.FastMap;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.GeneralException;
import org.ofbiz.base.util.UtilDateTime;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilXml;
import org.ofbiz.base.util.collections.MapStack;
import org.ofbiz.entity.GenericDelegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.condition.EntityCondition;
import org.ofbiz.entity.condition.EntityConditionList;
import org.ofbiz.entity.condition.EntityExpr;
import org.ofbiz.entity.condition.EntityOperator;
import org.ofbiz.entity.util.EntityUtil;
import org.ofbiz.order.order.OrderReadHelper;
import org.ofbiz.service.DispatchContext;
import org.ofbiz.service.GenericServiceException;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.service.ServiceUtil;
import org.ofbiz.widget.fo.FoFormRenderer;
import org.ofbiz.widget.html.HtmlScreenRenderer;
import org.ofbiz.widget.screen.ScreenRenderer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;


public class OagisShipmentServices {
    
    public static final String module = OagisShipmentServices.class.getName();

    protected static final HtmlScreenRenderer htmlScreenRenderer = new HtmlScreenRenderer();
    protected static final FoFormRenderer foFormRenderer = new FoFormRenderer();
    
    public static final String resource = "OagisUiLabels";
    
    public static Map showShipment(DispatchContext ctx, Map context) {
        InputStream in = (InputStream) context.get("inputStream");
        OutputStream out = (OutputStream) context.get("outputStream");
        LocalDispatcher dispatcher = ctx.getDispatcher();
        GenericDelegator delegator = ctx.getDelegator();
        GenericValue userLogin = null;
        try{
            Document doc = UtilXml.readXmlDocument(in, true, "ShowShipment");
            userLogin = delegator.findByPrimaryKey("UserLogin",UtilMisc.toMap("userLoginId","admin"));            
            Element shipmentElement = doc.getDocumentElement();
            shipmentElement.normalize();
            
            Element dataAreaElement = UtilXml.firstChildElement(shipmentElement, "n:DATAAREA");
            Element showShipmentElement = UtilXml.firstChildElement(dataAreaElement, "n:SHOW_SHIPMENT");
            Element shipment_N_Element = UtilXml.firstChildElement(showShipmentElement, "n:SHIPMENT");                                  
            String documentId =UtilXml.childElementValue(shipment_N_Element,"N2:DOCUMENTID");            
            String description =UtilXml.childElementValue(shipment_N_Element,"N2:DESCRIPTN");
            
            Element shipUnitElement = UtilXml.firstChildElement(showShipmentElement, "n:SHIPUNIT");                                   
            String shipUnitTrackingId =UtilXml.childElementValue(shipUnitElement,"N2:TRACKINGID");            
            
            Element invItem = UtilXml.firstChildElement(shipUnitElement, "n:INVITEM");            
            String invItemItem =UtilXml.childElementValue(invItem,"N2:ITEM");
            
            Element invDetail = UtilXml.firstChildElement(invItem, "n:INVDETAIL");
            String invDetailSerialNum =UtilXml.childElementValue(invDetail,"N1:SERIALNUM");
            
            /*Code for Issuing the Items*/
            List orderItemShipGrpInvReservations = FastList.newInstance();
            //GenericValue inventoryItem = null;
            try {                
                GenericValue shipment = delegator.findByPrimaryKey("Shipment", UtilMisc.toMap("shipmentId", documentId));                
                String shipGroupSeqId = shipment.getString("primaryShipGroupSeqId");                
                String originFacilityId = shipment.getString("originFacilityId");                              
                List shipmentItems = delegator.findByAnd("ShipmentItem", UtilMisc.toMap("shipmentId", documentId, "productId",invItemItem));                
                GenericValue shipmentItem =  EntityUtil.getFirst(shipmentItems);                
                String shipmentItemSeqId = shipmentItem.getString("shipmentItemSeqId");                
                //Now we have enough keys to lookup the right OrderShipment
                List orderShipments = delegator.findByAnd("OrderShipment", UtilMisc.toMap("shipmentId", documentId, "shipmentItemSeqId",shipmentItemSeqId));                
                GenericValue orderShipment =   EntityUtil.getFirst(orderShipments);                
                String orderId = orderShipment.getString("orderId");                
                String orderItemSeqId = orderShipment.getString("orderItemSeqId");                
                GenericValue product = delegator.findByPrimaryKey("Product",UtilMisc.toMap("productId",invItemItem));                
                String requireInventory = product.getString("requireInventory");
                if(requireInventory == null) {
                    requireInventory = "N";
                }                
                // Look for reservations in some status.
                orderItemShipGrpInvReservations = delegator.findByAnd("OrderItemShipGrpInvRes", UtilMisc.toMap("orderId", orderId,"orderItemSeqId",orderItemSeqId,"shipGroupSeqId",shipGroupSeqId));               
                GenericValue orderItemShipGrpInvReservation =   EntityUtil.getFirst(orderItemShipGrpInvReservations);                
                GenericValue inventoryItem = delegator.findByPrimaryKey("InventoryItem", UtilMisc.toMap("inventoryItemId",orderItemShipGrpInvReservation.get("inventoryItemId")));                
                String serialNumber = inventoryItem.getString("serialNumber");
                
                Map isitspastCtx = UtilMisc.toMap("orderId", orderId, "shipGroupSeqId", shipGroupSeqId, "orderItemSeqId", orderItemSeqId, "quantity", shipmentItem.get("quantity"), "quantityNotReserved", shipmentItem.get("quantity"));                
                isitspastCtx.put("productId", invItemItem);
                isitspastCtx.put("reservedDatetime", orderItemShipGrpInvReservation.get("reservedDatetime"));
                isitspastCtx.put("requireInventory", requireInventory);
                isitspastCtx.put("reserveOrderEnumId", orderItemShipGrpInvReservation.get("reserveOrderEnumId"));
                isitspastCtx.put("sequenceId", orderItemShipGrpInvReservation.get("sequenceId"));
                isitspastCtx.put("originFacilityId", originFacilityId);
                isitspastCtx.put("userLogin", userLogin);
                isitspastCtx.put("serialNumber", invDetailSerialNum);
                isitspastCtx.put("trackingNum", shipUnitTrackingId);
                isitspastCtx.put("inventoryItemId", orderItemShipGrpInvReservation.get("inventoryItemId"));                
                isitspastCtx.put("shipmentId", documentId);                                
                // Check if the inventory Item we reserved is same as Item shipped
                // If not then reserve Inventory Item                               
                try {                    
                    Map result = dispatcher.runSync("issueSerializedInvToShipmentPackageAndSetTracking", isitspastCtx);                                      
                } catch(Exception e) {
                    Debug.logInfo("========In catch =========", module);
                    return ServiceUtil.returnError("return error"+e);
                }
            } catch (Exception e) {
                return ServiceUtil.returnError("return error"+e);
            }
        }catch (Exception e){
            Debug.logError(e, module);
        }
        Map result = ServiceUtil.returnSuccess("Action performed successfuly");
        result.put("contentType","text/plain");
        return result;        
    }

    public static Map processShipment(DispatchContext ctx, Map context) {
        LocalDispatcher dispatcher = ctx.getDispatcher();
        GenericDelegator delegator = ctx.getDelegator();
        String orderId = (String) context.get("orderId");
        String shipmentId = (String) context.get("shipmentId");
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        Map result = ServiceUtil.returnSuccess();
        MapStack bodyParameters =  MapStack.create();
        if (userLogin == null) {
            try {
                userLogin = delegator.findByPrimaryKey("UserLogin", UtilMisc.toMap("userLoginId", "admin"));
            } catch (GenericEntityException e) {
                Debug.logError(e, "Error getting userLogin", module);
            }
        }
        GenericValue orderHeader = null;
        GenericValue orderItemShipGroup = null;
        try {
            orderHeader = delegator.findByPrimaryKey("OrderHeader", UtilMisc.toMap("orderId", orderId));
        } catch (GenericEntityException e) {
            Debug.logError(e, module);
            return ServiceUtil.returnError(e.getMessage());
        }
        GenericValue shipment =null;
        if (orderHeader != null) {
            String orderStatusId = orderHeader.getString("statusId");
            if (orderStatusId.equals("ORDER_APPROVED")) {
                try {
                    Map  cospResult= dispatcher.runSync("createOrderShipmentPlan", UtilMisc.toMap("orderId", orderId, "userLogin", userLogin));
                    shipmentId = (String) cospResult.get("shipmentId");
                } catch (GeneralException e) {
                    Debug.logError(e, module);
                    return ServiceUtil.returnError(e.getMessage());
                }
                try {
                    shipment = delegator.findByPrimaryKey("Shipment", UtilMisc.toMap("shipmentId", shipmentId));
                    bodyParameters.put("shipment", shipment);
                    OrderReadHelper orderReadHelper = new OrderReadHelper(orderHeader);
                    if(orderReadHelper.hasShippingAddress()) {
                        GenericValue  address = EntityUtil.getFirst(orderReadHelper.getShippingLocations());
                        bodyParameters.put("address", address);
                    }
                    String emailString = orderReadHelper.getOrderEmailString();
                    bodyParameters.put("emailString", emailString);

                    String contactMechId = shipment.getString("destinationTelecomNumberId");
                    GenericValue telecomNumber = delegator.findByPrimaryKey("TelecomNumber", UtilMisc.toMap("contactMechId", contactMechId));
                    bodyParameters.put("telecomNumber", telecomNumber);

                    List shipmentItems = delegator.findByAnd("ShipmentItem", UtilMisc.toMap("shipmentId", shipmentId));
                    bodyParameters.put("shipmentItems", shipmentItems);
                    
                    orderItemShipGroup = EntityUtil.getFirst(delegator.findByAnd("OrderItemShipGroup", UtilMisc.toMap("orderId", orderId)));
                    bodyParameters.put("orderItemShipGroup", orderItemShipGroup);
                } catch (GenericEntityException e) {
                    Debug.logError(e, module);
                }
                Set correspondingPoIdSet = new TreeSet();
                try {
                    List orderItems = delegator.findByAnd("OrderItem", UtilMisc.toMap("orderId", shipment.getString("primaryOrderId")));
                    Iterator oiIter = orderItems.iterator();
                    while (oiIter.hasNext()) {
                        GenericValue orderItem = (GenericValue) oiIter.next();
                        String correspondingPoId = orderItem.getString("correspondingPoId");
                        correspondingPoIdSet.add(correspondingPoId);
                        bodyParameters.put("correspondingPoIdSet", correspondingPoIdSet);
                    }
                } catch (GenericEntityException e) {
                    Debug.logError(e, module);
                }
                Set externalIdSet = new TreeSet();
                try {
                    GenericValue primaryOrderHeader = delegator.findByPrimaryKey("OrderHeader", UtilMisc.toMap("orderId", shipment.getString("primaryOrderId")));
                    externalIdSet.add(primaryOrderHeader.getString("externalId"));
                    bodyParameters.put("externalIdSet", externalIdSet);
                } catch (GenericEntityException e) {
                    Debug.logError(e, module);
                }
                // if order was a return replacement order (associated with return)
                List returnItemResponses =  null;
                List returnItemRespExprs = UtilMisc.toList(new EntityExpr("replacementOrderId", EntityOperator.NOT_EQUAL, null));
                EntityCondition returnItemRespCond = new EntityConditionList(returnItemRespExprs, EntityOperator.AND);
                // list of fields to select (initial list)
                List fieldsToSelect = FastList.newInstance();
                fieldsToSelect.add("replacementOrderId");
                try {
                    returnItemResponses = delegator.findByCondition("ReturnItemResponse", returnItemRespCond, fieldsToSelect, null);
                    Iterator rirIter = returnItemResponses.iterator();
                    while (rirIter.hasNext()) {
                        if (rirIter.next().equals(shipment.getString("primaryOrderId"))) {
                            bodyParameters.put("shipnotes", "RETURNLABEL");
                        }
                    }
                } catch (GenericEntityException e) {
                    Debug.logError(e, module);
                }
                String logicalId = UtilProperties.getPropertyValue("oagis.properties", "CNTROLAREA.SENDER.LOGICALID");
                bodyParameters.put("logicalId", logicalId);
                result.put("logicalId", logicalId);
                
                String authId = UtilProperties.getPropertyValue("oagis.properties", "CNTROLAREA.SENDER.AUTHID");
                bodyParameters.put("authId", authId);
                result.put("authId", authId);

                String referenceId = delegator.getNextSeqId("OagisMessageInfo");
                bodyParameters.put("referenceId", referenceId);
                result.put("referenceId", referenceId);
                    
                DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSS'Z'Z");
                Timestamp timestamp = UtilDateTime.nowTimestamp();
                String sentDate = dateFormat.format(timestamp);
                bodyParameters.put("sentDate", sentDate);
                result.put("sentDate", timestamp);
               
                // tracking shipper account
                String partyId = shipment.getString("partyIdTo");
                List partyCarrierAccounts = new ArrayList();
                try {
                    partyCarrierAccounts = delegator.findByAnd("PartyCarrierAccount", UtilMisc.toMap("partyId", partyId));
                    partyCarrierAccounts = EntityUtil.filterByDate(partyCarrierAccounts);
                    if (partyCarrierAccounts != null) {
                        Iterator pcaIter = partyCarrierAccounts.iterator();
                        while (pcaIter.hasNext()) {
                            GenericValue partyCarrierAccount = (GenericValue) pcaIter.next();
                            String carrierPartyId = partyCarrierAccount.getString("carrierPartyId");
                            if (carrierPartyId.equals(orderItemShipGroup.getString("carrierPartyId"))) {
                                String accountNumber = partyCarrierAccount.getString("accountNumber");
                                bodyParameters.put("shipperId", accountNumber);
                            }
                        }
                    }
                } catch (GenericEntityException e) {
                    Debug.logError(e, module);
                }
                bodyParameters.put("shipmentId", shipmentId);
                bodyParameters.put("orderId", orderId);
                bodyParameters.put("userLogin", userLogin);
                String bodyScreenUri = UtilProperties.getPropertyValue("oagis.properties", "Oagis.Template.ProcessShipment");
                OutputStream out = (OutputStream) context.get("outputStream");
                Writer writer = new OutputStreamWriter(out);
                ScreenRenderer screens = new ScreenRenderer(writer, bodyParameters, new HtmlScreenRenderer());
                try {
                    screens.render(bodyScreenUri);
                } catch (Exception e) {
                      Debug.logError(e, "Error rendering [text/xml]: ", module);
                }
                // prepare map to Create Oagis Message Info
                result.put("component", "INVENTORY");
                result.put("task", "SHIPREQUES"); // Actual value of task is "SHIPREQUEST" which is more than 10 char
                result.put("outgoingMessage", "Y");
                result.put("confirmation", "1");
                result.put("bsrVerb", "PROCESS");
                result.put("bsrNoun", "SHIPMENT");
                result.put("bsrRevision", "001");
                result.put("processingStatusId", orderStatusId);
                result.put("orderId", orderId);
                result.put("shipmentId", shipmentId);
                result.put("userLogin", userLogin);
            }
        }
        return result;
    }
    
    public static Map receiveDelivery(DispatchContext dctx, Map context) {
        LocalDispatcher dispatcher = dctx.getDispatcher();
        GenericDelegator delegator = dctx.getDelegator();
        String returnId = (String) context.get("returnId");
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        GenericValue returnHeader = null;
        GenericValue postalAddress =null;
        List returnItems = new ArrayList();
        String partyId = null;
        String orderId = null;
        if (returnId != null) {
            try {
                returnHeader = delegator.findByPrimaryKey("ReturnHeader", UtilMisc.toMap("returnId", returnId));
                String statusId = returnHeader.getString("statusId");
                if (statusId.equals("RETURN_ACCEPTED")) {
                    returnItems = delegator.findByAnd("ReturnItem", UtilMisc.toMap("returnId", returnId));
                    postalAddress = delegator.findByPrimaryKey("PostalAddress", UtilMisc.toMap("contactMechId", returnHeader.getString("originContactMechId")));
                            
                    // calculate total qty of return items in a shipping unit received
                    double itemQty = 0.0;
                    double totalQty = 0.0;
                    Iterator riIter = returnItems.iterator();
                    while (riIter.hasNext()) {
                        GenericValue returnItem = (GenericValue) riIter.next();
                        itemQty = returnItem.getDouble("returnQuantity").doubleValue();
                        totalQty = totalQty + itemQty;
                        orderId = returnItem.getString("orderId");
                    }
                    partyId = returnHeader.getString("fromPartyId");
                    List partyContactMechs = new ArrayList();
                    GenericValue contactMech = null;
                    GenericValue telecomNumber =null;
                    String emailString = null;
                    partyContactMechs = delegator.findByAnd("PartyContactMech", UtilMisc.toMap("partyId", partyId));
                    Iterator pcmIter = partyContactMechs.iterator();
                    while (pcmIter.hasNext()) {
                        GenericValue partyContactMech = (GenericValue) pcmIter.next();
                        String contactMechId = partyContactMech.getString("contactMechId");
                        contactMech = delegator.findByPrimaryKey("ContactMech", UtilMisc.toMap("contactMechId", contactMechId));
                        String contactMechTypeId = contactMech.getString("contactMechTypeId");
                        if(contactMechTypeId.equals("EMAIL_ADDRESS")) {
                            emailString = contactMech.getString("infoString");
                        }
                        if(contactMechTypeId.equals("TELECOM_NUMBER")) {
                            telecomNumber = delegator.findByPrimaryKey("TelecomNumber", UtilMisc.toMap("contactMechId", contactMechId));
                        }
                    }
                    String logicalId = UtilProperties.getPropertyValue("oagis.properties", "CNTROLAREA.SENDER.LOGICALID");
                    String authId = UtilProperties.getPropertyValue("oagis.properties", "CNTROLAREA.SENDER.AUTHID");
                    String referenceId = delegator.getNextSeqId("OagisMessageInfo");
                    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSS'Z'Z");
                    Timestamp timestamp = UtilDateTime.nowTimestamp();
                    String sentDate = dateFormat.format(timestamp);
                    Map bodyParameters = new HashMap();
                    bodyParameters.put("returnId", returnId);
                    bodyParameters.put("returnItems", returnItems);
                    bodyParameters.put("totalQty", new Double(totalQty));
                    bodyParameters.put("postalAddress", postalAddress);
                    bodyParameters.put("telecomNumber", telecomNumber);
                    bodyParameters.put("emailString", emailString);
                    bodyParameters.put("logicalId", logicalId);
                    bodyParameters.put("authId", authId);
                    bodyParameters.put("referenceId", referenceId);
                    bodyParameters.put("sentDate", sentDate);
                    bodyParameters.put("returnId", returnId);
                    String bodyScreenUri = UtilProperties.getPropertyValue("oagis.properties", "Oagis.Template.ReceiveDelivery");
                    Map emfsCtx = new HashMap();
                    emfsCtx.put("bodyParameters", bodyParameters);
                    emfsCtx.put("bodyScreenUri", bodyScreenUri);
                            
                    // export the message
                    try {
                        dispatcher.runSync("exportMsgFromScreen", emfsCtx);
                    } catch (GenericServiceException e) {
                        Debug.logError("Error in exporting message" + e.getMessage(), module);
                        return ServiceUtil.returnError("Error in exporting message");
                    }
                            
                    // prepare map to store BOD information
                    Map comiCtx = new HashMap();
                    comiCtx.put("logicalId", logicalId);
                    comiCtx.put("authId", authId);
                    comiCtx.put("referenceId", referenceId);
                    comiCtx.put("sentDate", timestamp);
                    comiCtx.put("component", "INVENTORY");
                    comiCtx.put("task", "RMA");  
                    comiCtx.put("outgoingMessage", "Y");
                    comiCtx.put("confirmation", "1");
                    comiCtx.put("bsrVerb", "RECEIVE");
                    comiCtx.put("bsrNoun", "DELIVERY");
                    comiCtx.put("bsrRevision", "001");
                    comiCtx.put("processingStatusId", statusId);        
                    comiCtx.put("returnId", returnId);
                    comiCtx.put("orderId", orderId);
                    comiCtx.put("userLogin", userLogin);
                    try {
                        dispatcher.runSync("createOagisMessageInfo", comiCtx);
                    } catch (GenericServiceException e) {
                          return ServiceUtil.returnError("Error in creating message info" + e.getMessage());
                    }
                }
            } catch (Exception e) {
                  Debug.logError("Error in Processing" + e.getMessage(), module);
            }
        }
        return ServiceUtil.returnSuccess("Service Completed Successfully");
    }
}
