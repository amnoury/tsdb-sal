List<AuthorizationRule> ruleBase; // database of all authorization rules
class AuthorizationRule{
	Subject sbj;
	Object obj;
	Action action;
	List<ValueConstraint> value_constraint;
	TemporalConstraint temporal_constraint;
} 
class Request:AuthorizationRule{/*inherites from AuthorizationRule class*/}
class Subject{/*attributes of subject*/}
class Object{/*attributes of object*/}
class Action{ enum{write, read, min, max, average, sum, multiply, count, c-min, c-max,c-average, c-sum, c-multiply, c-count}}
class ValueConstraint{
	float min;
	float max;
}
class TemporalConstraint{
	dateTime begin;
	dateTime end;
	int granularity; // in months
}
boolean isAggregateAction(Action ac){
	if(ac == (min|max|average|sum|multiply|count)){
		return true;
	}
	return false;
}
boolean isCompositeAction(Action ac){
	if(ac == (c-min|c-max|c-average| c-sum| c-multiply| c-count)){
		return true;
	}
	return false;
}
boolean isOwner(Subject s, Object o){
	if(s is owner of o){
		return true;
	}
	return false;
}
boolean isAdministrator(Subject s){
	if(s is administrator){
		return true;
	}
	return false;
}
boolean hasTemporalIntersection(AuthorizationRule rule, Request req){
	if( [rule.temporal_constraint.begin, rule.temporal_constraint.end] & 
		[req.temporal_constraint.begin, req.temporal_constraint.end] have intersection){
		return true;
	}
	return false;
}
boolean hasTemporalIntersection(AuthorizationRule rule, dateTime begin, dateTime end){
	if([rule.temporal_constraint.begin, rule.temporal_constraint.end] & [begin,end] have intersection){
		return true;
	}
	return false;
}
List<Object> getAllBaseTimeSeries(Object compositeTimeSeries){
	/* Code ommited for the sake of brevity */
	/* Traverse Object tree, find base time-series of compositeTimeSeries and return them as list<object> */
}

AuthorizationRule rewriteRequest(AuthorizationRule rule, Request req){
	Request revisedRequest;
	revisedRequest.sbj=req.sbj;
	revisedRequest.obj=req.obj;
	revisedRequest.action=req.action;
	revisedRequest.temporal_constraint.begin= max (rule.temporal_constraint.begin, req.temporal_constraint.begin);
	revisedRequest.temporal_constraint.end= min (rule.temporal_constraint.end, req.temporal_constraint.end);
	if (isAggregateAction(req.action)) {
		if(rule.action == Action.read){
			revisedRequest.temporal_constraint.granularity=req.temporal_constraint.granularity;
		}
		else{ // isAggregateAction(rule.action) == true
			revisedRequest.temporal_constraint.granularity=max(rule.temporal_constraint.granularity,req.temporal_constraint.granularity);
		}
	}
	revisedRequest.value_constraint= rule.value_constraint & req.value_constraint; // logical AND 
	return revisedRequest;
}
AuthorizationRule mergeReadRules(List<AuthorizationRule> readRules){
	/* Example: */
	/* readRules[0].temporal_constraint=[1/1/2017, 1/4/2017] , readRules[0].value_constraint=(x>100) */
	/* readRules[1].temporal_constraint=[1/2/2017, 1/8/2017] , readRules[0].value_constraint=(x>200) */
	/* readRules[2].temporal_constraint=[1/3/2017, 1/12/2017] , readRules[0].value_constraint=(x<1000) */
	AuthorizationRule mergedRule=readRules[0];
	for (int i=1;i<readRules.count; i++ ) {
		merged= (merged & readRules[i]); 
	}
	/* mergedRule.temporal_constraint=[1/3/2017, 1/4/2017] */
	/* mergedRule.value_constraint= (200<x<1000) */
	return mergedRule;
}
List<AuthorizationRule> getReadRulesFromBaseObjects(list<AuthorizationRule> childrenRules, int childrenCount){
	/* e.g. childrenRules={rule0, ..., rule4,rule5}, childrenRules.count=6 and childrenCount=3 */
	List<int,List<int>> sharedRules;

	for (int i=0; i<childrenRules.count; i++ ) {
		for (int j=0; j<childrenRules.count; j++ ) {
			if(hasTemporalIntersection(childrenRules[i],childrenRules[j])){
				sharedRules[i].add(j);
			}
		}
	}
	/* sharedRules[0]={0,2,4}, sharedRules[1]= {1,3}, sharedRules[2]= {0,2,4,5} 
	   sharedRules[3]= {1,3}, sharedRules[4]= {0,2,4},  sharedRules[5]={2,5} 
	   remove rules that do not have intersection with childrenCount-1 other rules. So, rules 1, 3 and 5 should be removed */
	for (int i=0;i< childrenRules.count; i++) {
		if(sharedRules[i].size <childrenCount){
			for (int j=0; j<sharedRules.count; j++) {
				sharedRules[j].remove(i);
			}
		}
	}
	/* sharedRules[0]={0,2,4}, sharedRules[1]= {}, sharedRules[2]= {0,2,4} 
	   sharedRules[3]= {}, sharedRules[4]= {0,2,4},  sharedRules[5]={2} */
	sharedRules.removeSmallSets(); // if sharedRules[i].size<children, then remove it. 
	/* sharedRules[0]={0,2,4}, sharedRules[2]= {0,2,4}, sharedRules[4]= {0,2,4} */
	sharedRules.removeDuplicates(); 
	/* sharedRules[0]= {0,2,4} */

	/* merge rules with temporal intersection */
	List<AuthorizationRule> mergedRules;
	for (int i=0; i<sharedRules.count; i++) {
		AuthorizationRule r= mergeReadRules(sharedRules[i]);
		mergedRules.add(r);
	}
	return mergedRules;
}
List<AuthorizationRule> getRulesByTimeInterval(Subject sbj, Object obj, dateTime begin, dateTime end){
		List<AuthorizationRule> result;
		foreach(rule in ruleBase){
			if(rule.sbj == sbj & 
				rule.obj == obj & 
						hasTemporalIntersection(rule,begin,end)){
					result.add(rule);
			}
		}
	return result;
}
List<AuthorizationRule> getRelatedRules(Request req){
	List<AuthorizationRule> subjectRules= getRulesByTimeInterval(req.sbj, req.obj, req.tc.begin, req.tc.end);
	List<AuthorizationRule> result; 
	/* for read request, lookup for read rule
	   for aggregation request, lookup for aggregation or read rule
	   for composition request, lookup for composition rule (read rule can not be defined on composite objects) */
	foreach(rule in subjectRules){
		if(rule.action == req.action | rule.action == Action.read){
			result.add(rule);
		}
	}
	// Also, subject can execute composite action if there exist read permission on all base time-series of composite object
	if(isCompositeAction(req.action)){
		List<Object> children=getAllBaseTimeSeries(req.obj);
		List<AuthorizationRule> childrenRules;
		foreach(child in children){
			childrenRules.add(getRulesByTimeInterval(req.sbj, child, req.tc.begin, req.tc.end));
		}
		List<AuthorizationRule> readRules=getReadRulesFromBaseObjects(childrenRules, children.count);
		result.add(readRules);
	}
	return result;
}
boolean executeRequest(Request req){
	if(isAdministrator(req.s)  | isOwner(req.s, req.o)) {
		/* execute the request */
		EXECUTE(req); 
		return true;
	}
	else{  
		if(req.action == Action.write){
			REJECT(req);
			Return flase;
		}
		else{
			List<AuthorizationRule> relatedRules=getRelatedRules(req);
			foreach(rule in readRules){
				Request revisedRequest=rewriteRequest(rule,req);
				EXECUTE(revisedRequest);
			}
			return true;
		}
	}
}